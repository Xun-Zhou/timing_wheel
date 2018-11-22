import com.utils.Timer;
import com.utils.TimerTask;

import java.util.concurrent.CountDownLatch;

public class Main {

    static int inCount = 0;

    static int runCount = 0;

    public static void main(String[] args) {
        CountDownLatch countDownLatch = new CountDownLatch(1000);
        Timer timer = new Timer();
        for(int i=1;i<=1000;i++){
            TimerTask timerTask = new TimerTask(i,()->{
                countDownLatch.countDown();
                int index = addRun();
                System.out.println(index+"----------执行");
            });
            timer.addTask(timerTask);
            System.out.println(i+"++++++++++加入");
            inCount++;
        }
        try {
            countDownLatch.await();
            System.out.println("inCount" + inCount);
            System.out.println("runCount" + runCount);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public synchronized static int addRun(){
        runCount++;
        return runCount;
    }
}
