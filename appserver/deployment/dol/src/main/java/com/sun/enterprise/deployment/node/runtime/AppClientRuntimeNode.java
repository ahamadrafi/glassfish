/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.deployment.node.runtime;

import com.sun.enterprise.deployment.ApplicationClientDescriptor;
import com.sun.enterprise.deployment.node.XMLElement;
import com.sun.enterprise.deployment.node.appclient.AppClientNode;
import com.sun.enterprise.deployment.xml.DTDRegistry;
import com.sun.enterprise.deployment.xml.RuntimeTagNames;
import com.sun.enterprise.deployment.xml.TagNames;
import com.sun.enterprise.deployment.xml.WebServicesTagNames;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Map;

/**
 * This node is responsible for saving all J2EE RI runtime
 * information for app clients
 *
 * @author Jerome Dochez
 * @version
 */
public class AppClientRuntimeNode extends RuntimeBundleNode<ApplicationClientDescriptor> {

    public AppClientRuntimeNode(ApplicationClientDescriptor descriptor) {
        super(descriptor);
        // trigger registration in standard node, if it hasn't happened
        serviceLocator.getService(AppClientNode.class);
    }


    public AppClientRuntimeNode() {
        this(null);
    }


    /**
     * Initialize the child handlers
     */
    @Override
    protected void init() {
        // we do not care about our standard DDS handles
        handlers = null;

        registerElementHandler(new XMLElement(TagNames.RESOURCE_REFERENCE), ResourceRefNode.class);
        registerElementHandler(new XMLElement(TagNames.EJB_REFERENCE), EjbRefNode.class);
        registerElementHandler(new XMLElement(TagNames.RESOURCE_ENV_REFERENCE), ResourceEnvRefNode.class);
        registerElementHandler(new XMLElement(TagNames.MESSAGE_DESTINATION_REFERENCE), MessageDestinationRefNode.class);
        registerElementHandler(new XMLElement(RuntimeTagNames.MESSAGE_DESTINATION), MessageDestinationRuntimeNode.class);
        registerElementHandler(new XMLElement(WebServicesTagNames.SERVICE_REF), ServiceRefNode.class);
        registerElementHandler(new XMLElement(RuntimeTagNames.JAVA_WEB_START_ACCESS), JavaWebStartAccessNode.class);

    }


    /**
     * register this node as a root node capable of loading entire DD files
     *
     * @param publicIDToDTD is a mapping between xml Public-ID to DTD
     * @return the doctype tag name
     */
    public static String registerBundle(Map publicIDToDTD) {
        publicIDToDTD.put(DTDRegistry.SUN_APPCLIENT_130_DTD_PUBLIC_ID, DTDRegistry.SUN_APPCLIENT_130_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPCLIENT_140_DTD_PUBLIC_ID, DTDRegistry.SUN_APPCLIENT_140_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPCLIENT_141_DTD_PUBLIC_ID, DTDRegistry.SUN_APPCLIENT_141_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPCLIENT_500_DTD_PUBLIC_ID, DTDRegistry.SUN_APPCLIENT_500_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPCLIENT_600_DTD_PUBLIC_ID, DTDRegistry.SUN_APPCLIENT_600_DTD_SYSTEM_ID);
        if (!restrictDTDDeclarations()) {
            publicIDToDTD.put(DTDRegistry.SUN_APPCLIENT_140beta_DTD_PUBLIC_ID,
                DTDRegistry.SUN_APPCLIENT_140beta_DTD_SYSTEM_ID);
        }
        return RuntimeTagNames.S1AS_APPCLIENT_RUNTIME_TAG;
    }


    /**
     * @return the XML tag associated with this XMLNode
     */
    @Override
    protected XMLElement getXMLRootTag() {
        return new XMLElement(RuntimeTagNames.S1AS_APPCLIENT_RUNTIME_TAG);
    }


    /**
     * @return the DOCTYPE that should be written to the XML file
     */
    @Override
    public String getDocType() {
        return DTDRegistry.SUN_APPCLIENT_600_DTD_PUBLIC_ID;
    }


    /**
     * @return the SystemID of the XML file
     */
    @Override
    public String getSystemID() {
        return DTDRegistry.SUN_APPCLIENT_600_DTD_SYSTEM_ID;
    }


    /**
     * @return NULL for all runtime nodes.
     */
    @Override
    public List<String> getSystemIDs() {
        return null;
    }


    /**
     * write the descriptor class to a DOM tree and return it
     *
     * @param parent node for the DOM tree
     * @param bundleDescriptor the descriptor to write
     * @return the DOM tree top node
     */
    @Override
    public Node writeDescriptor(Node parent, ApplicationClientDescriptor bundleDescriptor) {
        Node appClient = super.writeDescriptor(parent, bundleDescriptor);
        RuntimeDescriptorNode.writeCommonComponentInfo(appClient, bundleDescriptor);
        RuntimeDescriptorNode.writeMessageDestinationInfo(appClient, bundleDescriptor);
        JavaWebStartAccessNode.writeJavaWebStartInfo(appClient, bundleDescriptor.getJavaWebStartAccessDescriptor());
        return appClient;
    }


    /**
     * receives notification of the value for a particular tag
     *
     * @param element the xml element
     * @param value it's associated value
     */
    @Override
    public void setElementValue(XMLElement element, String value) {
        if (!element.getQName().equals(RuntimeTagNames.VERSION_IDENTIFIER)) {
            super.setElementValue(element, value);
        }
    }
}
