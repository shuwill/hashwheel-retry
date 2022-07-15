#### Task Retry with netty HashWheelTimer

example：

```java
    public class TestRetryTask extends HashWheelRetryTask<String> {

        protected TestRetryTask(String name) {
            super(name);
        }

        private int count = 0;

        @Override
        protected String retry() throws Exception {
            count++;
            if(count <= 2) {
                int a = 1/0;
                System.out.println(a);
            }
            return UUID.randomUUID().toString();
        }
        
    }

    TestRetryTask retryTask = new TestRetryTask("test retry");
    retryTask.setDelay(10);
    retryTask.setTimeUnit(TimeUnit.SECONDS);
    retryTask.setMaxTimes(3);
    String result = retryTask.execute();
```