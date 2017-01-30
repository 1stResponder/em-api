/**
 * Copyright (c) 2008-2016, Massachusetts Institute of Technology (MIT)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ardentmc.dhs.nics;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.fediz.core.Claim;
import org.apache.cxf.fediz.core.ClaimCollection;
import org.apache.cxf.fediz.spring.FederationUser;
import org.apache.cxf.fediz.spring.authentication.FederationAuthenticationToken;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

public class NICSClaimToHeaderFilter extends GenericFilterBean {
	
	private String headerName = "CUSTOM-uid";
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse resp = (HttpServletResponse) response;
		HttpServletRequest requestWrapper = req;
		String requestURI = req.getRequestURI();
		logger.info("requestURI: "+requestURI);
		try {

	        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
	        if (auth instanceof FederationAuthenticationToken) {
	        		        		        	
	            FederationAuthenticationToken fedAuthToken = (FederationAuthenticationToken)auth;	            
	            if (fedAuthToken.getUserDetails() instanceof FederationUser) {
	            	
	                ClaimCollection claims = ((FederationUser)fedAuthToken.getUserDetails()).getClaims();
	                Map<String, Object> claimValuesByTypeMap = new HashMap<String, Object>();
	                logger.info("FedAuth Claims found: "+claims.size());
	                for (Claim c: claims) {
	                	String claimType = c.getClaimType().toString();
						Object claimValue = c.getValue();
//						logger.info(claimType + ": " + claimValue);
	                	claimValuesByTypeMap.put(claimType, claimValue);
	                }
	                
	                if(claimValuesByTypeMap.containsKey("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress")) {
                		// email address claim
                		Object claimValue = claimValuesByTypeMap.get("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
						String emailAddress = (String)claimValue;
						logger.info("Translating NICS user based on emailaddress claim: "+emailAddress);
						requestWrapper = translateClaimToHeader(emailAddress, req, resp);	                	
	                } else {
		                // Try sub-claims first
		                Object idpClaimValue = claimValuesByTypeMap.get("http://identityserver.thinktecture.com/claims/identityprovider");
		                if(idpClaimValue != null) {	                	
		                	String idpClaim = (String)idpClaimValue;
		                	String subClaimType = "http://identityserver.thinktecture.com/claims/provider:"+idpClaim.toLowerCase();
		                	Object subClaim = claimValuesByTypeMap.get(subClaimType);
		                	JSONArray subClaimsJSONArray = new JSONArray(subClaim.toString());	
		                	Map<String, Object> subClaimValuesByTypeMap = new HashMap<String, Object>();
		                	for(int i = 0; i < subClaimsJSONArray.length(); i++) {
		                		JSONObject aSubClaimJSON = subClaimsJSONArray.getJSONObject(i);
		                		String aSubClaimType = aSubClaimJSON.getString("Type");
		                		String aSubClaimValue = aSubClaimJSON.getString("Value");
								subClaimValuesByTypeMap.put(aSubClaimType, aSubClaimValue);
		                	}
		                	
		                	String nameidentifierSubClaimValue = (String)subClaimValuesByTypeMap.get("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier");
	                		if(nameidentifierSubClaimValue != null) {
	                			
	    						logger.info("Translating NICS user based on emailaddress subClaim: "+nameidentifierSubClaimValue);
	    						requestWrapper = translateClaimToHeader(nameidentifierSubClaimValue, req, resp);
	            				
	                		} else {
	                			throw new RuntimeException("Could not find emailaddress claim for identity provider: "+idpClaim);
	                		}
		                	
		                }
	                }
	
	            } else {
	            	logger.info("FederationAuthenticationToken found but not FederationUser");
	            }
	            
	        } else {
	            logger.info("No FederationAuthenticationToken found in Spring Security Context.");
	        }
		} catch(Exception ex) {
			logger.error("Exception caught at the outer level.", ex);
		}
		chain.doFilter(requestWrapper, response);
	}
	
	private HttpServletRequest translateClaimToHeader(final String emailAddress, final HttpServletRequest req, final HttpServletResponse resp) {
	    HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(req) {
	        @Override
	        public String getHeader(String name) {
	            if(getHeaderName().equals(name)) {
	            	return emailAddress;
	            }
	            return super.getHeader(name);
	        }
	        
	        @Override
	        public Enumeration getHeaderNames() {
	            List<String> names = Collections.list(super.getHeaderNames());
	            names.add(getHeaderName());
	            return Collections.enumeration(names);
	        }
	        
	        @Override
	        public Enumeration getHeaders(String name) {
	        	
	            if(getHeaderName().equals(name)) {
	            	return Collections.enumeration(Collections.singletonList(emailAddress));
	            }
	            return super.getHeaders(name);	        	
	        }	        
	    };
	    return wrapper;
	}

	public String getHeaderName() {
		return headerName;
	}

	public void setHeaderName(String headerName) {
		this.headerName = headerName;
	}
	
	
}
