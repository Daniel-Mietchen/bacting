/* Copyright (c) 2009-2019  Egon Willighagen <egonw@users.sf.net>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contact: http://www.bioclipse.net/
 */
package net.bioclipse.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import com.hp.hpl.jena.n3.turtle.TurtleParseException;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.shared.NoReaderForLangException;
import com.hp.hpl.jena.shared.PrefixMapping;
import com.hp.hpl.jena.shared.SyntaxError;
import com.hp.hpl.jena.shared.impl.PrefixMappingImpl;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.domain.IStringMatrix;
import net.bioclipse.core.domain.StringMatrix;
import net.bioclipse.rdf.StringMatrixHelper;
import net.bioclipse.rdf.business.IJenaStore;
import net.bioclipse.rdf.business.IRDFStore;
import net.bioclipse.rdf.business.JenaModel;
import net.bioclipse.rdf.business.TDBModel;

public class RDFManager {

    public static final Integer CONNECT_TIME_OUT = 5000; 
    public static final Integer READ_TIME_OUT = 30000; 

	private String workspaceRoot;

	public RDFManager(String workspaceRoot) {
		this.workspaceRoot = workspaceRoot;
	}

    public IRDFStore createInMemoryStore() {
    	return new JenaModel();
    }

    public IRDFStore createStore(String tripleStoreDirectoryPath) {
    	return new TDBModel(tripleStoreDirectoryPath);
    }

    public IRDFStore createInMemoryStore(boolean ontologyModel) {
    	return new JenaModel(ontologyModel);
    }

    public StringMatrix sparqlRemote(
            String serviceURL,
            String sparqlQueryString) {
         Query query = QueryFactory.create(sparqlQueryString);
         QueryEngineHTTP qexec = (QueryEngineHTTP)QueryExecutionFactory.sparqlService(serviceURL, query);
         qexec.addParam("timeout", "" + CONNECT_TIME_OUT);
         PrefixMapping prefixMap = query.getPrefixMapping();

         StringMatrix table = null;
         try {
             ResultSet results = qexec.execSelect();
             table = StringMatrixHelper.convertIntoTable(prefixMap, results);
         } finally {
             qexec.close();
         }
         return table;
     }

    public IStringMatrix processSPARQLXML(byte[] queryResults, String originalQuery)
            throws BioclipseException {
    	PrefixMapping prefixMap = null;
        if (originalQuery != null) {
       	 try {
                Query query = QueryFactory.create(originalQuery);
                prefixMap = query.getPrefixMapping();
       	 } catch (Exception exception) {
       		 // could not parse the query for namespaces
       		 prefixMap = new PrefixMappingImpl();
       	 }
        }

        // now the Jena part
        ResultSet results = ResultSetFactory.fromXML(new ByteArrayInputStream(queryResults));
        StringMatrix table = StringMatrixHelper.convertIntoTable(prefixMap, results);

        return table;
    }

    public StringMatrix sparql(IRDFStore store, String queryString) throws IOException, BioclipseException,
    CoreException {
        if (!(store instanceof IJenaStore))
            throw new RuntimeException(
                "Can only handle IJenaStore's for now."
            );

        StringMatrix table = null;
        Model model = ((IJenaStore)store).getModel();
        Query query = QueryFactory.create(queryString);
        PrefixMapping prefixMap = query.getPrefixMapping();
        QueryExecution qexec = QueryExecutionFactory.create(query, model);
        try {
            ResultSet results = qexec.execSelect();
            table = StringMatrixHelper.convertIntoTable(prefixMap, results);
        } finally {
            qexec.close();
        }
        return table;
    }

    public long size(IRDFStore store) throws BioclipseException {
        if (!(store instanceof IJenaStore))
            throw new RuntimeException(
                "Can only handle IJenaStore's for now."
            );
        Model model = ((IJenaStore)store).getModel();
        return model.size();
    }

    public IRDFStore importFile(IRDFStore store, String rdfFile, String format)
    throws IOException, BioclipseException, CoreException {
    	return importFromStream(store, new FileInputStream(workspaceRoot + rdfFile), format);
    }

    public IRDFStore importFromStream(IRDFStore store, InputStream stream,
            String format)
    throws IOException, BioclipseException, CoreException {
        if (format == null) format = "RDF/XML";

        if (!(store instanceof IJenaStore))
            throw new RuntimeException(
                "Can only handle IJenaStore's for now."
            );
        
        Model model = ((IJenaStore)store).getModel();
        try {
        	model.read(stream, "", format);
        } catch (SyntaxError error) {
        	throw new BioclipseException(
        		"File format is not correct.",
        		error
        	);
        } catch (NoReaderForLangException exception) {
        	throw new BioclipseException(
            	"Unknown file format. Supported are \"RDF/XML\", " +
            	"\"N-TRIPLE\", \"TURTLE\" and \"N3\".",
            	exception
        	);
        } catch (TurtleParseException exception) {
        	throw new BioclipseException(
                "Error while parsing file: " +
                exception.getMessage(),
                exception
        	);
        }
        return store;
    }

    public IRDFStore importFromString(IRDFStore store, String rdfContent,
            String format)
    throws IOException, BioclipseException, CoreException {
    	InputStream input = new ByteArrayInputStream(rdfContent.getBytes());
    	return importFromStream(store, input, format);
    }

    public IRDFStore importURL(IRDFStore store, String url)
            throws IOException, BioclipseException, CoreException {
    	return importURL(store, url, null);
    }

    public IRDFStore importURL(IRDFStore store, String url,
    		Map<String, String> extraHeaders)
        throws IOException, BioclipseException, CoreException {
        	URL realURL = new URL(url);
        URLConnection connection = realURL.openConnection();
        connection.setConnectTimeout(CONNECT_TIME_OUT);
        connection.setReadTimeout(READ_TIME_OUT);
        connection.setRequestProperty(
            "Accept",
            "application/xml, application/rdf+xml"
        );
        // set the extra headers
        if (extraHeaders != null) {
        	for (String key : extraHeaders.keySet()) {
        		connection.setRequestProperty(key, extraHeaders.get(key));
        	}
        }
        try {
            InputStream stream = connection.getInputStream();
            importFromStream(store, stream, null);
            stream.close();
        } catch (UnknownHostException exception) {
            throw new BioclipseException(
                "Unknown or unresponsive host: " + realURL.getHost(), exception
            );
        }
        return store;
    }

    public String asRDFN3(IRDFStore store)
    throws BioclipseException {
    	return asRDF(store, "N3");
    }

    public String asTurtle(IRDFStore store)
    throws BioclipseException {
    	return asRDF(store, "TURTLE");
    }

    private String asRDF(IRDFStore store, String type)
    throws BioclipseException {
    	try {
    		ByteArrayOutputStream output = new ByteArrayOutputStream();
    		if (store instanceof IJenaStore) {
    			Model model = ((IJenaStore)store).getModel();
    			model.write(output, type);
    			output.close();
    			String result = new String(output.toByteArray());
    	    	return result;
    		} else {
    			throw new BioclipseException("Only supporting IJenaStore!");
    		}
    	} catch (IOException e) {
    		throw new BioclipseException("Error while writing RDF.", e);
    	}
    }

}