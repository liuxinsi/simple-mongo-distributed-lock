package com.lxs.smdl;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoCommandException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author liuxinsi
 * @mail akalxs@gmail.com
 */
public class MongoLock {
    private static final Logger LOG = LoggerFactory.getLogger(MongoLock.class);
    /**
     * lock collection name
     */
    private static final String LOCK_COLL_NAME = "D_LOCK";
    /**
     * expire time in second
     */
    private static final int EXPIRE_SECONDS = 60;

    private MongoClient mongoClient;
    private String dbName;
    private DB db;
    private ThreadLocal<LockKeeper> lockKeeperThreadLocal = new ThreadLocal<>();
    private final Object lock = new Object();

    public MongoLock(MongoClient mongoClient, String dbName) {
        this.mongoClient = mongoClient;
        this.dbName = dbName;

        DB db = getDatabase();
        if (!db.collectionExists(LOCK_COLL_NAME)) {
            LOG.debug("Lock collection isn't exist");
            db.getCollection(LOCK_COLL_NAME)
                    .createIndex(
                            new BasicDBObject("time", 1),
                            new BasicDBObject("expireAfterSeconds", EXPIRE_SECONDS)
                    );
            db.getCollection(LOCK_COLL_NAME)
                    .createIndex(
                            new BasicDBObject("lock", 1),
                            "lock_uniq", true
                    );
        }
    }

    private DB getDatabase() {
        if (db == null) {
            synchronized (lock) {
                if (db == null) {
                    this.db = new DB(mongoClient, dbName);
                }
            }
        }
        return db;
    }

    public boolean getLock(String lockName) {
        BasicDBObject query = new BasicDBObject();
        query.put("lock", lockName);
        query.put("ver", 1);

        BasicDBObject lockObj = updateLock(query);

        if (lockObj != null) {
            LOG.debug("get lock,start the lock keeper thread");

            LockKeeper lk = new LockKeeper(lockObj);
            lk.setDaemon(true);
            lk.start();
            lockKeeperThreadLocal.set(lk);
            Runtime.getRuntime().addShutdownHook(new Thread(lk::stopRunning));
            return true;
        }
        return false;
    }

    private BasicDBObject updateLock(BasicDBObject q) {
        DB db = getDatabase();
        DBCollection coll = db.getCollection(LOCK_COLL_NAME);

        BasicDBObject u = new BasicDBObject();
        u.put("$currentDate", new BasicDBObject("time", true));
        u.put("$inc", new BasicDBObject("ver", 1));

        try {
            return (BasicDBObject) coll.findAndModify(q, null, null, false, u, true, true);
        } catch (MongoCommandException e) {
            // duplicate ex
            if (e.getErrorCode() == 11000) {
                return null;
            } else {
                throw e;
            }
        }
    }

    private BasicDBObject removeLock(BasicDBObject q) {
        DB db = getDatabase();
        DBCollection coll = db.getCollection(LOCK_COLL_NAME);

        return (BasicDBObject) coll.findAndRemove(q);
    }


    public void release() {
        if (lockKeeperThreadLocal.get() == null) {
            LOG.warn("current thread has not the lock,release nothing");
            return;
        }
        lockKeeperThreadLocal.get().stopRunning();
    }

    private class LockKeeper extends Thread {
        private Logger log = LoggerFactory.getLogger(LockKeeper.class);
        private BasicDBObject lockObj;
        private boolean running = true;

        private LockKeeper(BasicDBObject lockObj) {
            this.lockObj = lockObj;
            setName(lockObj.get("lock") + "-Keeper");
        }

        @Override
        public void run() {
            log.info("lock keeper thread start");
            while (running) {
                BasicDBObject query = new BasicDBObject();
                query.put("lock", lockObj.get("lock"));
                query.put("ver", lockObj.get("ver"));

                lockObj = updateLock(query);
                if (lockObj == null) {
                    log.warn("update lock fail! maybe is remove on outside");
                }

                try {
                    Thread.sleep(EXPIRE_SECONDS / 3 * 1000);
                } catch (InterruptedException e) {
                    log.warn(e.getMessage(), e);
                }
            }
            log.warn("lock keeper thread exit");
        }

        private void stopRunning() {
            running = false;
            this.interrupt();

            lockObj = removeLock(lockObj);
            log.warn("remove lock:{}", lockObj);
        }
    }
}
