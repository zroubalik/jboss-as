/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.jpa.hibernate.secondlevelcache;

import static org.junit.Assert.fail;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Hibernate Second level cache tests
 * 
 * @author Zbynek Roubalik
 */
@RunWith(Arquillian.class)
public class Hibernate2LCTestCase {

    private static final String ARCHIVE_NAME = "jpa_Hibernate2LCTestCase";
    
    private static final String CACHE_REGION_NAME = ARCHIVE_NAME+".jar#mypc."+Hibernate2LCTestCase.class.getPackage().getName()+'.';

    private static final String persistence_xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
			+ "<persistence xmlns=\"http://java.sun.com/xml/ns/persistence\" version=\"1.0\">"
			+ "  <persistence-unit name=\"mypc\">"
			+ "    <description>Persistence Unit."
			+ "    </description>"
			+ "  <jta-data-source>java:jboss/datasources/ExampleDS</jta-data-source>"
			+ " <shared-cache-mode>ENABLE_SELECTIVE</shared-cache-mode>"
			+ "<properties> <property name=\"hibernate.hbm2ddl.auto\" value=\"create-drop\"/>"
			+ "<property name=\"hibernate.show_sql\" value=\"true\"/>"
			+ "<property name=\"hibernate.cache.use_second_level_cache\" value=\"true\"/>"
			+ "<property name=\"hibernate.cache.use_query_cache\" value=\"true\"/>"
			+ "<property name=\"hibernate.generate_statistics\" value=\"true\"/>"
			+ "</properties>"
			+ "  </persistence-unit>"
			+ "  <persistence-unit name=\"mypc2\">"
			+ "    <description>Persistence Unit."
			+ "    </description>"
			+ "  <jta-data-source>java:jboss/datasources/ExampleDS</jta-data-source>"
			+ " <shared-cache-mode>ENABLE_SELECTIVE</shared-cache-mode>"
			+ "<properties> <property name=\"hibernate.hbm2ddl.auto\" value=\"create-drop\"/>"
			+ "<property name=\"hibernate.show_sql\" value=\"true\"/>"
			+ "<property name=\"hibernate.cache.use_second_level_cache\" value=\"true\"/>"
			+ "<property name=\"hibernate.cache.use_query_cache\" value=\"true\"/>"
			+ "<property name=\"hibernate.generate_statistics\" value=\"true\"/>"
			+ "</properties>"
			+ "  </persistence-unit>"
			+ "  <persistence-unit name=\"mypc_no_2lc\">"
			+ "    <description>Persistence Unit."
			+ "    </description>"
			+ "  <jta-data-source>java:jboss/datasources/ExampleDS</jta-data-source>"
			+ " <shared-cache-mode>NONE</shared-cache-mode>"
			+ "<properties> <property name=\"hibernate.hbm2ddl.auto\" value=\"create-drop\"/>"
			+ "<property name=\"hibernate.show_sql\" value=\"true\"/>"
			+ "<property name=\"hibernate.cache.use_second_level_cache\" value=\"false\"/>"
			+ "<property name=\"hibernate.cache.use_query_cache\" value=\"false\"/>"
			+ "<property name=\"hibernate.generate_statistics\" value=\"true\"/>"
			+ "</properties>" + "  </persistence-unit>" + "</persistence>";

    @Deployment
    public static Archive<?> deploy() {

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar");
        jar.addClasses(Hibernate2LCTestCase.class,
            Employee.class,
            SFSB2LC.class
        );

        jar.addAsResource(new StringAsset(persistence_xml), "META-INF/persistence.xml");
        return jar;
    }

    @ArquillianResource
    private InitialContext iniCtx;

    protected <T> T lookup(String beanName, Class<T> interfaceType) throws NamingException {
        return interfaceType.cast(iniCtx.lookup("java:global/" + ARCHIVE_NAME + "/" + beanName + "!" + interfaceType.getName()));
    }

    protected <T> T rawLookup(String name, Class<T> interfaceType) throws NamingException {
        return interfaceType.cast(iniCtx.lookup(name));
    }

    
    // When caching is disabled, no extra action is done or exception happens
 	// even if the code marks an entity and/or a query as cacheable
    @Test
 	public void testDisabledCache() throws Exception {
 	
    	SFSB2LC sfsb = lookup("SFSB2LC", SFSB2LC.class);
 		String message = sfsb.disabled2LCCheck();
 		
 		if (!message.equals("OK")){
 			fail(message);
 		}
 	}

 	// When entity caching is enabled, loading all entities at once
 	// will put all entities in the cache. During the SAME session, 
 	// when looking up for the ID of an entity which was returned by
 	// the original query, no SQL queries should be executed.
    @Test
 	public void testEntityCacheSameSession() throws Exception {

    	SFSB2LC sfsb = lookup("SFSB2LC", SFSB2LC.class);
 		String message = sfsb.sameSessionCheck(CACHE_REGION_NAME);
 		
 		if (!message.equals("OK")){
 			fail(message);
 		}
 	}

 	// When entity caching is enabled, loading all entities at once
 	// will put all entities in the cache. During the SECOND session, 
 	// when looking up for the ID of an entity which was returned by
 	// the original query, no SQL queries should be executed.
    @Test
 	public void testEntityCacheSecondSession() throws Exception {
    	
    	SFSB2LC sfsb = lookup("SFSB2LC", SFSB2LC.class);
 		String message = sfsb.secondSessionCheck(CACHE_REGION_NAME);
 		
 		if (!message.equals("OK")){
 			fail(message);
 		}

 	}
 	
    // When query caching is enabled, running the same query twice 
  	// without any operations between them will perform SQL queries only once.
  	@Test
  	public void testSameQueryTwice() throws Exception {

  		SFSB2LC sfsb = lookup("SFSB2LC", SFSB2LC.class);
  		String message = sfsb.sameQueryTwice();
  		
  		if (!message.equals("OK")){
  			fail(message);
  		}
  	}
  	
  	// When query caching is enabled, running the same query twice 
   	// without any operations between them will perform SQL queries only once.
   	@Test
   	public void testEvictEntityCache() throws Exception {

   		SFSB2LC sfsb = lookup("SFSB2LC", SFSB2LC.class);
   		String message = sfsb.evict2LCCheck(CACHE_REGION_NAME);
   		
   		if (!message.equals("OK")){
   			fail(message);
   		}
   	}
  	
  	//When query caching is enabled, running a query to return all entities of a class 
  	// and then adding one entity of such class would invalidate the cache
  	@Test
  	public void testInvalidateQuery() throws Exception {

  		SFSB2LC sfsb = lookup("SFSB2LC", SFSB2LC.class);
  		String message = sfsb.invalidateQuery();
  		
  		if (!message.equals("OK")){
  			fail(message);
  		}
  	}
  	
  	// Check if evicting query cache is working as expected
  	@Test
  	public void testEvictQueryCache() throws Exception {
  		
  		SFSB2LC sfsb = lookup("SFSB2LC", SFSB2LC.class);
  		String message = sfsb.evictQueryCacheCheck();
  		
  		if (!message.equals("OK")){
  			fail(message);
  		}

  	}

 }
