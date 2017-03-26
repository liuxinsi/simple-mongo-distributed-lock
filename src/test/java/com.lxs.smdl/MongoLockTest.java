package com.lxs.smdl;

import com.mongodb.MongoClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author liuxinsi
 * @mail akalxs@gmail.com
 */
public class MongoLockTest {
    private MongoLock mongoLock;

    @Before
    public void init() {
        MongoClient mongoClient = new MongoClient("127.0.0.1", 27017);
        mongoLock = new MongoLock(mongoClient, "testdb");
    }

    @Test
    public void test() {
        try {
            boolean b = mongoLock.getLock("lock1");
            if (b) {
                System.out.println("do something");
                Thread.sleep(5000L);
            } else {
                System.out.println("cant not get the lock");
            }
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        } finally {
            mongoLock.release();
        }
    }

    @Test
    public void testConcurrent() {
        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try {
                    boolean b = mongoLock.getLock("lock1");
                    if (b) {
                        System.out.println("do something");
                        Thread.sleep(5000L);
                    } else {
                        System.out.println("cant not get the lock");
                    }
                } catch (InterruptedException e) {
                    Assert.fail(e.getMessage());
                } finally {
                    mongoLock.release();
                }
            }).start();
        }

        try {
            Thread.sleep(20000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
