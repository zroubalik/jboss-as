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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.ejb.Stateful;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.persistence.PersistenceUnit;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;

/**
 * SFSB for Second level cache tests
 * 
 * @author Zbynek Roubalik
 */
@Stateful
@TransactionManagement(TransactionManagementType.CONTAINER)
public class SFSB2LC {
	@PersistenceUnit(unitName = "mypc")
	SessionFactory sessionFactory;

	@PersistenceUnit(unitName = "mypc_no_2lc")
	SessionFactory sessionFactoryNo2LC;

	
	/**
	 * Check if disabling 2LC works as expected
	 */
	public String disabled2LCCheck() {

		Statistics stats = sessionFactoryNo2LC.getStatistics();
		Session session = sessionFactoryNo2LC.openSession();
		
		try {
			// check if entities are NOT cached in 2LC
			String names[] = stats.getSecondLevelCacheRegionNames();
			assertEquals("There aren't any 2LC regions.", 0, names.length);

			createEmployee(session, "Martin", "Prague 132", 1);
			assertEquals("There aren't any puts in the 2LC.", 0,stats.getSecondLevelCachePutCount());

			// check if queries are NOT cached in 2LC
			Employee emp = getEmployeeQuery(session, 1);
			assertNotNull("Employee returned", emp);
			assertEquals("There aren't any query puts in the 2LC.", 0,stats.getQueryCachePutCount());
			
		}catch (AssertionError e) {
			return e.getMessage();
		}	finally{
			session.close();
		}
		return "OK";
	}
	

	/**
	 *  Checking entity 2LC in one Hibernate Session
	 */
	public String sameSessionCheck(String CACHE_REGION_NAME) {
		
		Statistics stats = sessionFactory.getStatistics();
		stats.clear();
		SecondLevelCacheStatistics emp2LCStats = stats.getSecondLevelCacheStatistics(CACHE_REGION_NAME+"Employee");
		Session session = sessionFactory.openSession();
		
		try{
			// add new entities and check if they are put in 2LC
			createEmployee(session, "Peter", "Ostrava", 2);
			createEmployee(session, "Tom", "Brno", 3);
			assertEquals("There are 2 puts in the 2LC"+generateEntityCacheStats(emp2LCStats), 2, emp2LCStats.getPutCount());
			
			// loading all Employee entities should put in 2LC one Employee entity from previous test method
			List<?> empList = getAllEmployeesQuery(session);
			assertEquals("There are 3 entities.", empList.size(), 3);
			assertEquals("There are 3 entities in the 2LC"+generateEntityCacheStats(emp2LCStats), 3, emp2LCStats.getElementCountInMemory());
			
			// clear session
			session.clear();
			
			// entity should be loaded from 2L cache, we'are expecting hit in 2L cache
			Employee emp = getEmployee(session, 2);
			emp.getName();
			assertNotNull("Employee returned", emp);
			assertEquals("Expected 1 hit in cache"+generateEntityCacheStats(emp2LCStats), 1,  emp2LCStats.getHitCount());
			
		}catch (AssertionError e) {
			return e.getMessage();
		}	finally{
			session.close();
		}
		return "OK";
	}
	
	
	/**
	 *  Checking entity stored in a cache in a different Hibernate Session
	 */
	public String secondSessionCheck(String CACHE_REGION_NAME) {
		
		Statistics stats = sessionFactory.getStatistics();
		stats.clear();
		SecondLevelCacheStatistics emp2LCStats = stats.getSecondLevelCacheStatistics(CACHE_REGION_NAME+"Employee");
		Session session = sessionFactory.openSession();
		
		try{	
			// loading entity stored in previous session, we'are expecting hit in cache
			Employee emp = getEmployee(session, 2);
			emp.getName();
			assertNotNull("Employee returned", emp);
			assertEquals("Expected 1 hit in cache"+generateEntityCacheStats(emp2LCStats), 1,  emp2LCStats.getHitCount());
			
		}catch (AssertionError e) {
			return e.getMessage();
		}	finally{
			session.close();
		}
		return "OK";
	}
	
	
	/**
	 * Check if eviction of entity cache is working
	 */
	public String evict2LCCheck(String CACHE_REGION_NAME){

		Statistics stats = sessionFactory.getStatistics();
		stats.clear();
		SecondLevelCacheStatistics emp2LCStats = stats.getSecondLevelCacheStatistics(CACHE_REGION_NAME+"Employee");
		
		try{	
			assertTrue("Expected entities stored in the cache"+generateEntityCacheStats(emp2LCStats), emp2LCStats.getElementCountInMemory() > 0);
			
			// evict cache and check if is empty
			sessionFactory.getCache().evictEntityRegions();
			assertEquals("Expected no entities stored in the cache"+generateEntityCacheStats(emp2LCStats), 0, emp2LCStats.getElementCountInMemory());
			
			
		}catch (AssertionError e) {
			return e.getMessage();
		}	
		return "OK";
	}
	
	
	/**
	 *  Check if query cache works as expected, running same query twice - second should hit the cache 
	 */
	public String sameQueryTwice(){
		
		Statistics stats = sessionFactory.getStatistics();
		stats.clear();
		Session session = sessionFactory.openSession();
		
		try{
			int id = 2;
			String queryString = "from Employee e where e.id="+id;
			
			Query query = session.createQuery(queryString);
			query.setCacheable(true);
			QueryStatistics queryStats = stats.getQueryStatistics(queryString);
						
			// first query call
			Employee emp = (Employee) query.uniqueResult();
			emp.getName();
			assertEquals("Expected 1 put in cache"+generateQueryCacheStats(queryStats), 1,  queryStats.getCachePutCount());
			
			// second query call
			emp = (Employee) query.uniqueResult();
			emp.getName();
			assertEquals("Expected 1 hit in cache"+generateQueryCacheStats(queryStats), 1,  queryStats.getCacheHitCount());

		}catch (AssertionError e) {
			return e.getMessage();
		}	finally{
			session.close();
		}
		return "OK";
	}
	
	
	/**
	 *  Query cache is invalidated and restored then
	 */
	public String invalidateQuery(){
		
		Statistics stats = sessionFactory.getStatistics();
		stats.clear();
		Session session = sessionFactory.openSession();

		try{
			String queryString = "from Employee e where e.id > 1";
			QueryStatistics queryStats = stats.getQueryStatistics(queryString);
			Query query = session.createQuery(queryString);
			query.setCacheable(true);
			
			
			// query - this call should fill the cache
			query.list();
			assertEquals("Expected 1 put in cache"+generateQueryCacheStats(queryStats), 1,  queryStats.getCachePutCount());
			assertEquals("Expected no hits in cache"+generateQueryCacheStats(queryStats), 0,  queryStats.getCacheHitCount());
						
			// query - should hit cache
			query.list();
			assertEquals("Expected 1 hit in cache"+generateQueryCacheStats(queryStats), 1,  queryStats.getCacheHitCount());
						
			// invalidate cache
			createEmployee(session, "Newman", "Paul", 4);
						
			// first call should miss and the second should hit the cache
			query.list();
			assertEquals("Expected 2x miss in cache"+generateQueryCacheStats(queryStats), 2,  queryStats.getCacheMissCount());

			query.list();
			assertEquals("Expected 2 hits in cache"+generateQueryCacheStats(queryStats), 2,  queryStats.getCacheHitCount());
			

		}catch (AssertionError e) {
			return e.getMessage();
		}	finally{
			session.close();
		}
		return "OK";
	}

	
	/**
	 * Check if eviction of query cache is working
	 */
	public String evictQueryCacheCheck(){
		
		Statistics stats = sessionFactory.getStatistics();
		stats.clear();
		Session session = sessionFactory.openSession();

		try{
			String queryString = "from Employee e where e.id > 2";
			QueryStatistics queryStats = stats.getQueryStatistics(queryString);
			Query query = session.createQuery(queryString);
			query.setCacheable(true);

			// query - this call should fill the cache
			query.list();
			assertEquals("Expected 1 put in cache"+generateQueryCacheStats(queryStats), 1,  queryStats.getCachePutCount());
			assertEquals("Expected no hits in cache"+generateQueryCacheStats(queryStats), 0,  queryStats.getCacheHitCount());
			
			// query - should hit cache
			query.list();
			assertEquals("Expected 1 hit in cache"+generateQueryCacheStats(queryStats), 1,  queryStats.getCacheHitCount());
			
			// this should evict query cache -> expected second put in the cache
			sessionFactory.getCache().evictQueryRegions();
			query.list();
			assertEquals("Expected 2 puts in cache"+generateQueryCacheStats(queryStats), 2,  queryStats.getCachePutCount());

		}catch (AssertionError e) {
			return e.getMessage();
		}	finally{
			session.close();
		}
		return "OK";
	}
	
	/**
	 * Generate query cache statistics for put, hit and miss count as one String
	 */
	public String generateQueryCacheStats(QueryStatistics stats){
		String result = "(hitCount="+stats.getCacheHitCount()
				+", missCount="+stats.getCacheMissCount()
				+", putCount="+stats.getCachePutCount()+").";
		
		return result;
	}
	
	
	/**
	 * Generate entity cache statistics for put, hit and miss count as one String
	 */
	public String generateEntityCacheStats(SecondLevelCacheStatistics stats){
		String result = "(hitCount="+stats.getHitCount()
				+", missCount="+stats.getMissCount()
				+", putCount="+stats.getPutCount()+").";
		
		return result;
	}
	
	
	/**
	 * Create employee in provided Session
	 */
	public void createEmployee(Session session, String name, String address,
			int id) {
		Employee emp = new Employee();
		emp.setId(id);
		emp.setAddress(address);
		emp.setName(name);
		try {
			session.persist(emp);
			session.flush();
		} catch (Exception e) {
			throw new RuntimeException(	"transactional failure while persisting employee entity", e);
		}
	}

	
	/**
	 * Load employee from provided Session
	 */
	public Employee getEmployee(Session session, int id) {
		Employee emp = (Employee) session.load(Employee.class, id);
		return emp;
	}

	
	/**
	 * Load employee using Query from provided Session
	 */
	public Employee getEmployeeQuery(Session session, int id) {

		Query query;

		query = session.createQuery("from Employee e where e.id=:id");

		query.setParameter("id", id);
		query.setCacheable(true);

		return (Employee) query.uniqueResult();
	}

	
	/**
	 * Load all employees using Query from provided Session
	 */
	@SuppressWarnings("unchecked")
	public List<Employee> getAllEmployeesQuery(Session session) {

		Query query;

		query = session.createQuery("from Employee");
		query.setCacheable(true);

		return query.list();
	}

}
