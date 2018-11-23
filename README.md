## 时间轮
    时间轮是一种环形数据结构，类似于时钟，秒针、分针、时针分别为一层，每层分成多个格子，每个格子中存放任务集合，一个单独的线程推进时间一格一格的移动，并执行格子中的任务。
    TimingWheel并非简单的环形时间轮，而是多层级时间轮，每个时间轮由多个时间格组成，每个时间格为一个时间间隔，底层的时间格跨度较小，然后随着延迟任务延迟时间的长短逐层变大；
    如下图，下层的时间轮每个时间格为1ms，整个时间轮为10ms，而上面一层的时间轮中时间格为10ms，整个时间轮为100ms，
    上级时间轮添加的规则为：当前currentTime为上级时间轮的startMs，当前interval为上级时间轮的tickDuration，ticksPerWheel相同；
    简单点说就是上层时间轮跨度为当前的M倍，时间格为当前的N倍；
    时间轮常用于延时任务，在Netty、akka、Quartz、Zookeeper等高性能组件中都存在时间轮定时器的踪影。
- 时间轮数据结构

    ![时间轮数据结构](https://github.com/Xun-Zhou/timing_wheel/blob/master/timing_wheel.png "时间轮数据结构")
    
- 时间轮名词解释
    
      时间格：环形结构中用于存放延迟任务的区块
      指针(currentTime):指向当前操作的时间格，代表当前时间
      格数(ticksPerWheel):时间轮中时间格的个数
      间隔(tickDuration):每个时间格之间的间隔
      总间隔(interval):当前时间轮总间隔，也就是等于ticksPerWheel*tickDuration

## kafka时间轮
    在Kafka中应用了大量的延迟操作，但在Kafka中 并没用使用JDK自带的Timer或是DelayQueue用于延迟操作，而是使用自己开发的DelayedOperationPurgatory组件用于管理延迟操作，
    Kafka这类分布式框架有大量延迟操作并且对性能要求及高，而java.util.Timer与java.util.concurrent.DelayQueue的插入和删除复杂度都为对数阶O(log n)并不能满足Kafka性能要求，
    所以Kafka实现了基于时间轮的定时任务组件，该时间轮定时任务实现的插入与删除（开始定时器与暂停定时器）的时间复杂度都为常数阶O(1)。

- Timer
    
      Timer是kafka中的定时器类，定义了共客户端调用的方法。SystemTimer是对Timer的具体实现
      taskExecutor：过期任务执行线程，为了不影响性能，过期任务单独开辟线程执行
      delayQueue：一个Timer只有一个delayQueue，Timer中所有timingWheel共用，用于获取过期任务
      timingWheel：最底层时间轮tickMs(间隔)为1ms,wheelSize(格数)为20

    ![Timer](https://github.com/Xun-Zhou/timing_wheel/blob/master/timer.png "Timer")
    
    关键代码
```scala
   //添加任务
   def add(timerTask: TimerTask): Unit = {
      readLock.lock()
      try {
        addTimerTaskEntry(new TimerTaskEntry(timerTask, timerTask.delayMs + Time.SYSTEM.hiResClockMs))
      } finally {
        readLock.unlock()
      }
   }
   //添加任务，失败直接执行
   private def addTimerTaskEntry(timerTaskEntry: TimerTaskEntry): Unit = {
      if (!timingWheel.add(timerTaskEntry)) {
        if (!timerTaskEntry.cancelled)
          taskExecutor.submit(timerTaskEntry.timerTask)
      }
   }
   //获取过期任务，推进时间，任务执行或降轮重入
   def advanceClock(timeoutMs: Long): Boolean = {
      var bucket = delayQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
      if (bucket != null) {
        writeLock.lock()
        try {
          while (bucket != null) {
            timingWheel.advanceClock(bucket.getExpiration())
            bucket.flush(reinsert)
            bucket = delayQueue.poll()
          }
        } finally {
          writeLock.unlock()
        }
        true
      } else {
        false
      }
   }
```

- TimingWheel
        
      interval：时间轮时间范围tickMs * wheelSize
      buckets：TimerTaskList数组，长度对应wheelSize
      currentTime：当前时间startMs - (startMs % tickMs)，取整为tickMs的倍数
      overflowWheel：上级时间轮
    
    ![TimingWheel](https://github.com/Xun-Zhou/timing_wheel/blob/master/TimingWheel.png "TimingWheel")

    关键代码
```scala
    //添加或获取上级时间轮
    private[this] def addOverflowWheel(): Unit = {
        synchronized {
          if (overflowWheel == null) {
            overflowWheel = new TimingWheel(
              tickMs = interval,
              wheelSize = wheelSize,
              startMs = currentTime,
              taskCounter = taskCounter,
              queue
            )
          }
        }
      }
    //添加任务 失败返回false直接执行
    def add(timerTaskEntry: TimerTaskEntry): Boolean = {
        val expiration = timerTaskEntry.expirationMs
        if (timerTaskEntry.cancelled) {
          //取消
          false
        } else if (expiration < currentTime + tickMs) {
          //已过期
          false
        } else if (expiration < currentTime + interval) {
          //当前时间轮可以容纳该任务
          val virtualId = expiration / tickMs
          val bucket = buckets((virtualId % wheelSize.toLong).toInt)
          bucket.add(timerTaskEntry)
          if (bucket.setExpiration(virtualId * tickMs)) {
            queue.offer(bucket)
          }
          true
        } else {
          //加入上级时间轮
          if (overflowWheel == null) addOverflowWheel()
          overflowWheel.add(timerTaskEntry)
        }
      }
    //推进时间
    def advanceClock(timeMs: Long): Unit = {
        if (timeMs >= currentTime + tickMs) {
          currentTime = timeMs - (timeMs % tickMs)
          //推进上级时间轮时间
          if (overflowWheel != null) overflowWheel.advanceClock(currentTime)
        }
      }
```

- TimerTaskList
     
     TimerTaskEntry：用于封装TimerTask
    
   ![TimerTaskList](https://github.com/Xun-Zhou/timing_wheel/blob/master/TimerTaskList.png "TimerTaskList")

- TimerTask
    
     trait TimerTask extends Runnable继承java Runnable接口

   ![TimerTask](https://github.com/Xun-Zhou/timing_wheel/blob/master/TimerTask.png "TimerTask")
    
     