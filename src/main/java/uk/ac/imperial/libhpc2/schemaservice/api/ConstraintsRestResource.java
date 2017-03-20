/*
 * Copyright (c) 2017, Imperial College London
 * Copyright (c) 2017, The University of Edinburgh
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the names of the copyright holders nor the names of their
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * -----------------------------------------------------------------------------
 *
 * This file is part of the TemPSS - Templates and Profiles for Scientific 
 * Software - service, developed as part of the libhpc projects 
 * (http://www.imperial.ac.uk/lesc/projects/libhpc).
 *
 * We gratefully acknowledge the Engineering and Physical Sciences Research
 * Council (EPSRC) for their support of the projects:
 *   - libhpc: Intelligent Component-based Development of HPC Applications
 *     (EP/I030239/1).
 *   - libhpc Stage II: A Long-term Solution for the Usability, Maintainability
 *     and Sustainability of HPC Software (EP/K038788/1).
 */
package uk.ac.imperial.libhpc2.schemaservice.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.template.PebbleTemplate;

import uk.ac.ic.prism.jhc02.csp.CSPInitException;
import uk.ac.ic.prism.jhc02.csp.CSPParseException;
import uk.ac.ic.prism.jhc02.csp.CSProblemDefinition;
import uk.ac.ic.prism.jhc02.csp.Constraint;
import uk.ac.ic.prism.jhc02.csp.Variable;
import uk.ac.imperial.libhpc2.schemaservice.ConstraintsException;
import uk.ac.imperial.libhpc2.schemaservice.TempssObject;
import uk.ac.imperial.libhpc2.schemaservice.UnknownTemplateException;

/**
 * Jersey REST class representing the template endpoint
 * @author jhc02
 *
 */
@Component
@Path("constraints")
public class ConstraintsRestResource {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(ConstraintsRestResource.class.getName());

    /**
     * ServletContext obejct used to access template data
     * Injected via @Context annotation
     */
    ServletContext _context;

    @Context
    public void setServletContext(ServletContext pContext) {
        this._context = pContext;
        LOG.debug("Servlet context injected: " + pContext);
    }

    // Use the pebble engine to render the HTML constraint info
	@Autowired
	private PebbleEngine _pebbleEngine;
    
	/*
	public void setPebbleEngine(PebbleEngine pEngine) {
		this._pebbleEngine = pEngine;
	}
	*/
    @GET
    @Produces("application/json")
    @Path("{templateId}")
    public Response getConstraintInfoJSON(@PathParam("templateId")  String templateId) {
    	CSProblemDefinition problem = null;
    	try {
    		problem = getConstraintData(templateId);
    	} catch (UnknownTemplateException e) {
    		LOG.error("Specified template ID <" + templateId + "> doesn't exist: " + e.getMessage());
    		return Response.status(Response.Status.NOT_FOUND).build();
    	} catch(ConstraintsException e) {
    		LOG.error("Error getting constraint info for template <" + templateId + 
    				">: " + e.getMessage());
    		return Response.status(Response.Status.NOT_FOUND).build();
    	}
    	
    	JSONObject responseJson = new JSONObject();
    	// Process the constraint satisfaction problem data to be returned to the caller as JSON.
    	List<Variable> vars = problem.getVariables();
    	List<Constraint> constraints = problem.getConstraints();
    	
    	// Define a map that we'll populate when iterating through vars for use when 
    	// preparing constraint data
    	Map<String, Variable> varMap = new HashMap<String, Variable>(vars.size());
    	// Build variables JSON object
    	JSONArray varArray = new JSONArray();
    	for(Variable v : vars) {
    		varMap.put(v.getName(), v);
    		JSONArray valArray = new JSONArray(v.getValues());
    		JSONObject varObject = new JSONObject();
    		try {
				varObject.put("name", v.getName());
				varObject.put("domain", valArray);
			} catch (JSONException e) {
				LOG.error("ERROR adding variable data to JSON object: " + e.getMessage());
			}
    		varArray.put(varObject);
    	}
    	
    	JSONArray constraintArray = new JSONArray();
    	for(Constraint c : constraints) {
    		try {
    			JSONObject constraintObject = new JSONObject();
	    		constraintObject.put("variable1", c.getVariable1Name());
	    		constraintObject.put("variable2", c.getVariable2Name());
	    		
	    		Variable v1 = varMap.get(c.getVariable1Name());
	    		JSONArray mappingArray = new JSONArray();
				// For each value in v1, get all the valid mappings
	    		for(String value : v1.getValues()) {
	    			JSONObject mappingItem = new JSONObject();
	    			mappingItem.put("sourceVar", v1.getName());
	    			mappingItem.put("sourceValue", value);
	    			List<String> targetVals = c.getValidValues(v1.getName(), value);
	    			mappingItem.put("targetValues", new JSONArray(targetVals));
	    			mappingArray.put(mappingItem);
	    		}
	    		
	    		constraintObject.put("mappings", mappingArray);
	    		constraintArray.put(constraintObject);
    		} catch (JSONException e) {
				LOG.error("ERROR converting constraint <{}> JSON object: {}",
						 c.getName(), e.getMessage());
			}
    	}
    	
    	try {
    		responseJson.put("variables", varArray);
    		responseJson.put("constraints", constraintArray);
		} catch (JSONException e) {
			LOG.error("ERROR adding variable array to response JSON object: " + e.getMessage());
		}
    	
    	return Response.ok(responseJson.toString(), MediaType.APPLICATION_JSON).build();
    }
    
    @GET
    @Produces("text/html")
    @Path("{templateId}")
    public Response getConstraintInfoHTML(@PathParam("templateId")  String templateId) {
    	CSProblemDefinition problem = null;
    	try {
    		problem = getConstraintData(templateId);
    	} catch (UnknownTemplateException e) {
    		LOG.error("Specified template ID <" + templateId + "> doesn't exist: " + e.getMessage());
    		return Response.status(Response.Status.NOT_FOUND).build();
    	} catch(ConstraintsException e) {
    		LOG.error("Error getting constraint info for template <" + templateId + 
    				">: " + e.getMessage());
    		return Response.status(Response.Status.NOT_FOUND).build();
    	}
    	
    	List<Variable> vars = problem.getVariables();
    	List<Constraint> constraints = problem.getConstraints();

    	// Process the constraint satisfaction problem data to be returned to the caller as HTML.
    	
    	// Render the template
    	Map<String, Object> templateContext = new HashMap<String, Object>();
    	templateContext.put("templateName", templateId);
    	
    	StringWriter sw = new StringWriter();
    	try {
    		PebbleTemplate tpl = this._pebbleEngine.getTemplate("constraint_info");
    		tpl.evaluate(sw, templateContext);
    	} catch (PebbleException e) {
			LOG.error("Unable to get constraint info template for rendering: " + e.getMessage());
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		} catch (IOException e) {
			LOG.error("IO error during template rendering: " + e.getMessage());
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}
    	String responseStr = sw.toString();
    	
    	return Response.ok(responseStr, MediaType.TEXT_HTML).build();
    }
    
    private CSProblemDefinition getConstraintData(String templateId) 
    		throws UnknownTemplateException, ConstraintsException {
		TempssObject metadata = TemplateResourceUtils.getTemplateMetadata(templateId, this._context);
		String constraintFile = metadata.getConstraints();
		if(constraintFile == null) throw new ConstraintsException("There is no constraint file " +
				"configured for this template <" + templateId + ">.");
		
		// Now that we have the name of the constraint file we can create an 
		// instance of a constraint satisfaction problem definition based on this file.
		// The file is loaded as a resource from the jar file.
		CSProblemDefinition definition = null;
		InputStream xmlResource = getClass().getClassLoader().getResourceAsStream("META-INF/Constraints/" + constraintFile);
		if(xmlResource == null) {
			LOG.error("Unable to access constraint file <" + constraintFile + "> as resource.");
			throw new ConstraintsException("The constraint XML file could not be accessed.");
		}
		try {
			definition = CSProblemDefinition.fromXML(xmlResource);
		} catch (CSPInitException e) {
			LOG.error("Error setting up constraint definition object: " + e.getMessage());
			throw new ConstraintsException("Error setting up constraint definition object: " + e.getMessage(), e);
		} catch (CSPParseException e) {
			LOG.error("Error parsing constraints XML data: " + e.getMessage());
			throw new ConstraintsException("Error parsing constraints XML data.", e);
		}

		return definition;
    }
}