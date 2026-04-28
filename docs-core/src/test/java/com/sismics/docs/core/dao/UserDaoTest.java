package com.sismics.docs.core.dao;

import com.sismics.docs.core.dao.criteria.UserCriteria;
import com.sismics.docs.core.dao.dto.UserDto;
import com.sismics.docs.core.model.jpa.User;
import com.sismics.util.context.ThreadLocalContext;
import org.junit.Before;
import org.junit.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.Query;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UserDaoTest {
    private EntityManager em;

    @Before
    public void setUp() {
        ThreadLocalContext.cleanup();
        em = mock(EntityManager.class);
        ThreadLocalContext.get().setEntityManager(em);
    }

    @Test
    public void testAuthenticateNoResult() {
        Query q = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(q);
        when(q.setParameter(eq("username"), any())).thenReturn(q);
        when(q.getSingleResult()).thenThrow(new NoResultException());

        UserDao dao = new UserDao();
        assertNull(dao.authenticate("bob", "secret"));
    }

    @Test
    public void testAuthenticateBadPasswordAndDisabledAndSuccess() {
        Query q = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(q);
        when(q.setParameter(eq("username"), any())).thenReturn(q);

        // Good hashed password for "secret"
        String goodHash = BCrypt.withDefaults().hashToString(10, "secret".toCharArray());

        User user = new User();
        user.setPassword(goodHash);

        // Wrong password
        when(q.getSingleResult()).thenReturn(user);
        UserDao dao = new UserDao();
        assertNull(dao.authenticate("bob", "wrong"));

        // Disabled user should return null even with correct password
        user.setDisableDate(new Date());
        assertNull(dao.authenticate("bob", "secret"));

        // Enabled user with correct password returns user
        user.setDisableDate(null);
        assertSame(user, dao.authenticate("bob", "secret"));
    }

    @Test
    public void testCreateDuplicateAndSuccess() throws Exception {
        Query q = mock(Query.class);
        when(em.createQuery(contains("select u from User u where u.username"))).thenReturn(q);
        when(q.setParameter(eq("username"), any())).thenReturn(q);

        // Duplicate username
        when(q.getResultList()).thenReturn(Collections.singletonList(new Object()));
        UserDao dao = new UserDao();
        User u = new User();
        u.setUsername("bob");
        u.setPassword("pass");
        try {
            dao.create(u, "admin");
            fail("Expected AlreadyExistingUsername exception");
        } catch (Exception e) {
            assertEquals("AlreadyExistingUsername", e.getMessage());
        }

        // Success path
        when(q.getResultList()).thenReturn(Collections.emptyList());
        // Provide EMF and PersistenceUnitUtil for AuditLogUtil
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        PersistenceUnitUtil pu = mock(PersistenceUnitUtil.class);
        when(em.getEntityManagerFactory()).thenReturn(emf);
        when(emf.getPersistenceUnitUtil()).thenReturn(pu);
        when(pu.getIdentifier(any())).thenReturn("some-id");

        // Persist does nothing on mock
        User newUser = new User();
        newUser.setUsername("alice");
        newUser.setPassword("password");

        String id = dao.create(newUser, "admin");
        assertNotNull(id);
        verify(em).persist(newUser);
    }

    @Test
    public void testHashPasswordEnvCases() throws Exception {
        UserDao dao = new UserDao();
        Method m = UserDao.class.getDeclaredMethod("hashPassword", String.class);
        m.setAccessible(true);

        // Backup original env
        String envKey = com.sismics.docs.core.constant.Constants.BCRYPT_WORK_ENV;
        Map<String, String> original = new HashMap<>(System.getenv());

        try {
            setEnv(envKey, "5");
            String h1 = (String) m.invoke(dao, "pwd1");
            assertTrue(h1.contains("$"));
            String cost1 = extractCost(h1);
            assertEquals("05", cost1);

            setEnv(envKey, "100"); // out of range -> fallback
            String h2 = (String) m.invoke(dao, "pwd2");
            String cost2 = extractCost(h2);
            assertEquals(String.format("%02d", com.sismics.docs.core.constant.Constants.DEFAULT_BCRYPT_WORK), cost2);

            setEnv(envKey, "notanumber"); // NumberFormatException path
            String h3 = (String) m.invoke(dao, "pwd3");
            String cost3 = extractCost(h3);
            assertEquals(String.format("%02d", com.sismics.docs.core.constant.Constants.DEFAULT_BCRYPT_WORK), cost3);
        } finally {
            // try to restore original keys
            for (String k : original.keySet()) {
                setEnv(k, original.get(k));
            }
        }
    }

    @Test
    public void testFindByCriteriaAndAggregates() {
        // findByCriteria -> native query
        Query qNative = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(qNative);

        Object[] row = new Object[] {"id1", "bob", "b@e.com", new Timestamp(1000L), 10L, 20L, "totp", null};
        when(qNative.getResultList()).thenReturn(Collections.singletonList(row));

        UserCriteria criteria = new UserCriteria();
        criteria.setSearch("bob");

        UserDao dao = new UserDao();
        List<UserDto> list = dao.findByCriteria(criteria, null);
        assertEquals(1, list.size());
        UserDto dto = list.get(0);
        assertEquals("id1", dto.getId());
        assertEquals("bob", dto.getUsername());
        assertEquals("b@e.com", dto.getEmail());

        // getGlobalStorageCurrent
        Query qSum = mock(Query.class);
        when(em.createNativeQuery(contains("sum(u.USE_STORAGECURRENT_N)"))).thenReturn(qSum);
        when(qSum.getSingleResult()).thenReturn(123L);
        assertEquals(123L, dao.getGlobalStorageCurrent());

        // getActiveUserCount
        Query qCount = mock(Query.class);
        when(em.createNativeQuery(contains("count(u.USE_ID_C)"))).thenReturn(qCount);
        when(qCount.getSingleResult()).thenReturn(5L);
        // parameters will be set inside method; mock setParameter to return query
        when(qCount.setParameter(anyString(), any())).thenReturn(qCount);
        assertEquals(5L, dao.getActiveUserCount());
    }

    // Helper to extract bcrypt cost (two digits between second and third $)
    private String extractCost(String hash) {
        // format: $2y$10$...
        String[] parts = hash.split("\\$");
        if (parts.length > 2) {
            return parts[2];
        }
        return "";
    }

    // Attempts to set environment variable for the running JVM (best-effort for tests)
    @SuppressWarnings({"unchecked"})
    private static void setEnv(String key, String value) throws Exception {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field m = cl.getDeclaredField("m");
            m.setAccessible(true);
            Map<String, String> map = (Map<String, String>) m.get(env);
            if (value == null) {
                map.remove(key);
            } else {
                map.put(key, value);
            }
        } catch (NoSuchFieldException e) {
            // Fallback for other JVMs (Windows)
            Class<?> pe = Class.forName("java.lang.ProcessEnvironment");
            try {
                Field theCaseInsensitiveEnvironment = pe.getDeclaredField("theCaseInsensitiveEnvironment");
                theCaseInsensitiveEnvironment.setAccessible(true);
                Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironment.get(null);
                if (value == null) {
                    cienv.remove(key);
                } else {
                    cienv.put(key, value);
                }
            } catch (NoSuchFieldException nsfe) {
                // ignore if neither approach works
            }
        }
    }
}
