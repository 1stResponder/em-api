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
import java.util.*;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.cxf.fediz.core.Claim;
import org.apache.cxf.fediz.core.ClaimCollection;
import org.apache.cxf.fediz.spring.authentication.FederationAuthenticationToken;
import org.apache.cxf.fediz.spring.FederationUser;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

public class NICSClaimToHeaderFilter extends GenericFilterBean {
	
	private String headerName = "CUSTOM-uid";
	private static final String ERROR_MESSAGE_KEY = "errorMessageKey";
	private static final String LOGIN_ERROR_CONFIGURATION_MESSAGE = "login.error.configuration.message";
	private static final String ERROR_DESCRIPTION_KEY = "errorDescriptionKey";
	private static final String LOGIN_ERROR_APIERROR_DESCRIPTION = "login.error.apierror.description";
	private static final String FAILED_JSP_PATH = "login/loginFailed.jsp";
	private static String HOME_PAGE = "home.html";
	private static String DEFAULT_REQUEST_URI = "/em-api/";

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse resp = (HttpServletResponse) response;
		HttpServletRequest requestWrapper = req;
		String requestURI = req.getRequestURI();
		logger.info("requestURI: "+requestURI);
		try {

			//Do not cache the home page to allow the filter to redirect if the user
			//is not validated
			if(requestURI.equalsIgnoreCase(DEFAULT_REQUEST_URI) || requestURI.endsWith(HOME_PAGE))
			{
				resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
		        resp.setHeader("Pragma", "no-cache");
		        resp.setDateHeader("Expires", 0);
			}

	        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

	        if (auth instanceof FederationAuthenticationToken) {
	        		        		        	
	            FederationAuthenticationToken fedAuthToken = (FederationAuthenticationToken)auth;

	            if (fedAuthToken.getUserDetails() instanceof FederationUser) {
	            	
            		String username = fedAuthToken.getUserDetails().getUsername();

            		if(username != null && !username.isEmpty())
            		{
						logger.info("Looking up NICS user based on username claim: " + username);
						requestWrapper = translateClaimToHeader(username, req, resp);
					} else
					{
						throw new RuntimeException("Could not find username claim");
					}
					
	            } else {
	            	logger.info("FederationAuthenticationToken found but not FederationUser");
	            }
	            
	        } else {
	            logger.info("No FederationAuthenticationToken found in Spring Security Context.");
	        }

		} catch(Exception ex) {
			logger.error("Exception caught at the outer level.", ex);
			//redirectToErrorGeneric(req, resp);
		}

		chain.doFilter(requestWrapper, response);
	}

	private void redirectToErrorGeneric(final HttpServletRequest req, final HttpServletResponse resp) {
		req.setAttribute(ERROR_MESSAGE_KEY, LOGIN_ERROR_CONFIGURATION_MESSAGE);
		req.setAttribute(ERROR_DESCRIPTION_KEY, LOGIN_ERROR_APIERROR_DESCRIPTION);
		try {
			req.getRequestDispatcher(FAILED_JSP_PATH).forward(req, resp);
		} catch (ServletException | IOException e) {
			logger.error("Could not redirect to the login error page due to the following error.", e);
		}
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
