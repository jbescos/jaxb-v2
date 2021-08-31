/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
 
package com.sun.xml.bind.v2.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.Test;

public class ClassCastExceptionTest {

    static final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<testEntity>\n" +
            "    <dateTime>2021-03-25T09:34:49.877</dateTime>\n" +
            "</testEntity>";

    @Test
    public void accessorCast() throws JAXBException {
        System.setProperty("com.sun.xml.bind.v2.runtime.reflect.opt.OptimizedAccessorFactory.noOptimization","false");
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        // Using jdk's internal JAXB to unmarshall
        Thread.currentThread().setContextClassLoader(CustomJaxbClassLoader.internalJaxbLoader(parent));
        JAXBContext contextInternal = com.sun.xml.bind.v2.runtime.JAXBContextImpl
                .newInstance(TestEntity.class, TestEntity.DateTime.class);
        TestEntity unmarshaled = (TestEntity) contextInternal.createUnmarshaller()
                .unmarshal(new StringReader(xml));
        System.out.println(unmarshaled);
        // And org.glassfish.jaxb:jaxb-runtime:2.3.3 for marshalling same entity
        Thread.currentThread().setContextClassLoader(CustomJaxbClassLoader.riJaxbLoader(parent));
        JAXBContext contextGlassfish = com.sun.xml.bind.v2.runtime.JAXBContextImpl
                .newInstance(TestEntity.class, TestEntity.DateTime.class);
        Marshaller mar = contextGlassfish.createMarshaller();
        StringWriter writer = new StringWriter();
        mar.marshal(unmarshaled, writer);
        System.out.println(writer);
    }


    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TestEntity {
        private DateTime dateTime;

        public TestEntity() {
        }

        public DateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(final DateTime dateTime) {
            this.dateTime = dateTime;
        }

        @Override
        public String toString() {
            return String.valueOf(dateTime.value);
        }

        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "dateTime", propOrder = {
                "value"
        })
        public static class DateTime {

            @XmlValue
            @XmlSchemaType(name = "dateTime")
            protected XMLGregorianCalendar value;

            public XMLGregorianCalendar getValue() {
                return value;
            }

            public void setValue(XMLGregorianCalendar value) {
                this.value = value;
            }
        }
    }

    public static class CustomJaxbClassLoader extends ClassLoader {
        static {
            URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {

                @Override
                public URLStreamHandler createURLStreamHandler(String protocol) {
                    if("customjaxb".equals(protocol)){
                        return new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(final URL u) throws IOException {
                                return new URLConnection(u) {
                                    @Override
                                    public InputStream getInputStream() throws IOException {
                                        System.out.println("Custom JAXB context factory "+u.getHost());
                                        return new ByteArrayInputStream(u.getHost().getBytes());
                                    }
        
                                    @Override
                                    public void connect() throws IOException {
                                        //noop
                                    }
                                };
                            }
                        };
                    }
                    return null;
                }
                
            });
        }
    
        private final String contextFactory;
    
        private CustomJaxbClassLoader(final ClassLoader parent, String contextFactory) {
            super(parent);
            this.contextFactory = contextFactory;
        }
    
        public static ClassLoader internalJaxbLoader(final ClassLoader parent) {
            return new CustomJaxbClassLoader(parent, "com.sun.xml.internal.bind.v2.ContextFactory");
        }
    
        public static ClassLoader riJaxbLoader(final ClassLoader parent) {
            return new CustomJaxbClassLoader(parent, "com.sun.xml.bind.v2.ContextFactory");
        }
    
        @Override
        public URL getResource(final String name) {
            if (name.equals("META-INF/services/javax.xml.bind.JAXBContext")) {
                try {
                    return new URL("customjaxb://" + contextFactory);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            return super.getResource(name);
        }
    }
}
