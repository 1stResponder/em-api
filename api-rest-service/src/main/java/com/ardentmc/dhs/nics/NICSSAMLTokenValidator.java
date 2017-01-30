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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.fediz.core.Claim;
import org.apache.cxf.fediz.core.ClaimTypes;
import org.apache.cxf.fediz.core.saml.SAMLTokenValidator;
import org.opensaml.xml.XMLObject;
import org.w3c.dom.Element;

import org.apache.log4j.Logger;

public class NICSSAMLTokenValidator extends SAMLTokenValidator {
	private static Logger logger = Logger.getLogger(NICSSAMLTokenValidator.class);
	
	@Override
    protected List<Claim> parseClaimsInAssertion(
            org.opensaml.saml2.core.Assertion assertion) {
        List<org.opensaml.saml2.core.AttributeStatement> attributeStatements = assertion
                .getAttributeStatements();
        if (attributeStatements == null || attributeStatements.isEmpty()) {
            logger.debug("No attribute statements found");
            return Collections.emptyList();
        }

        List<Claim> collection = new ArrayList<Claim>();
        Map<String, Claim> claimsMap = new HashMap<String, Claim>();

        for (org.opensaml.saml2.core.AttributeStatement statement : attributeStatements) {
//            logger.debug("parsing statement: {}", statement.getElementQName());
            List<org.opensaml.saml2.core.Attribute> attributes = statement
                    .getAttributes();
            for (org.opensaml.saml2.core.Attribute attribute : attributes) {
                if (logger.isDebugEnabled()) {
                    logger.debug("parsing attribute: " + attribute.getName());
                }
                Claim c = new Claim();
                // Workaround for CXF-4484 
                // Value of Attribute Name not fully qualified
                // if NameFormat is http://schemas.xmlsoap.org/ws/2005/05/identity/claims
                // but ClaimType value must be fully qualified as Namespace attribute goes away
                URI attrName;
                try {
                	attrName = URI.create(attribute.getName());
                } catch(Exception e) {
                	String origAttrName = attribute.getName();
                	
                	if(origAttrName.contains(" ")) {
                		logger.warn("Could not parse attribute name as URI for: "+ origAttrName+ ", will try again substituting spaces for underscores.");
	                	String attrNameWithoutSpaces = origAttrName.replace(' ', '_');
						attrName = URI.create(attrNameWithoutSpaces);
                	} else {
                		logger.warn("Could not parse attribute name as URI for: "+ origAttrName+", skipping attribute.");
                		continue;
                	}
                }
                if (ClaimTypes.URI_BASE.toString().equals(attribute.getNameFormat())
                    && !attrName.isAbsolute()) {
                	
                    c.setClaimType(URI.create(ClaimTypes.URI_BASE + "/" + attrName.toString()));
                } else {
                    c.setClaimType(attrName);
                }
                c.setIssuer(assertion.getIssuer().getNameQualifier());
                
                List<String> valueList = new ArrayList<String>();
                for (XMLObject attributeValue : attribute.getAttributeValues()) {
                    Element attributeValueElement = attributeValue.getDOM();
                    String value = attributeValueElement.getTextContent();
//                    logger.debug(" [{}]", value);
                    valueList.add(value);
                }
                mergeClaimToMap(claimsMap, c, valueList);
            }
        }
        collection.addAll(claimsMap.values());
        return collection;

    }	
}
