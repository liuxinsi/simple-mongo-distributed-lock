# simple-mongo-distributed-lock
# About
基于MongoDB TTL的分布锁实现，避免因为仅仅需要一个简单的分布锁引入额外的技术栈。

# Sample
```
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
```

