package com.example.controller;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.RetryNTimes;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.concurrent.TimeUnit;

/**
 * @Author Devere19
 * @Date 2022/8/17 10:41
 * @Version 1.0
 */
@RestController
public class BiSheController {

    private static final String ZK_ADDRESS = "127.0.0.1:2181";

    private static final String ZK_LOCK_PATH = "/zkLock";

    static CuratorFramework client = null;

    static {
        //连接zk
        client = CuratorFrameworkFactory.newClient(ZK_ADDRESS,
                new RetryNTimes(10, 5000));
        client.start();
    }

    // 分布式锁
    // @Autowired
    // private Redisson redisson;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @GetMapping("/login")
    public String login(String username, HttpSession session, HttpServletRequest request) {
        session.setAttribute("username", username);
        return username + ",端口" + request.getLocalPort();
    }

    @GetMapping("/getUser")
    public String getUser(HttpSession session, HttpServletRequest request) {
        String username = (String) session.getAttribute("username");
        return session.getId() + "," + username + ",端口" + request.getLocalPort();
    }

    // @PostMapping("/checkTeacher")
    // public String checkteacher(String teacherName) {
    //     // SpringBoot操作Redis
    //     String lockKey = "lockKey";
    //     RLock redissonLock = null;
    //     String num = "";
    //     try {
    //         redissonLock = redisson.getLock("lockKey");//加锁
    //         redissonLock.tryLock(30, TimeUnit.SECONDS);//超时时间：每间隔10秒（1/3）
    //         num = stringRedisTemplate.opsForValue().get(teacherName);
    //         int n = Integer.parseInt(num);
    //
    //         if (n > 0) {
    //             n = n - 1;
    //             stringRedisTemplate.opsForValue().set("lkz", n + "");
    //             //正常选择老师
    //             System.out.println("当前名额：" + n);
    //         } else {
    //             return "名额已满";
    //         }
    //     } catch (InterruptedException e) {
    //         e.printStackTrace();
    //     } finally {
    //         redissonLock.unlock();//释放锁
    //     }
    //     return num;
    // }
    @PostMapping("/checkTeacher")
    public String checkteacher(String teacherName) {
        InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        String num = "";
        try {
            if (lock.acquire(6000, TimeUnit.SECONDS)) {
                // System.out.println("拿到了锁");
                //业务逻辑
                num = stringRedisTemplate.opsForValue().get(teacherName);
                int n = Integer.parseInt(num);
                if (n > 0) {
                    n = n - 1;
                    stringRedisTemplate.opsForValue().set("lkz", n + "");
                    //正常选择老师
                    System.out.println("当前名额：" + n);
                } else {
                    return "名额已满";
                }
                // System.out.println("任务完毕，该释放锁了");
            }
        } catch (Exception e) {
            System.out.println("业务异常");
            e.printStackTrace();
        }finally {
            try {
                lock.release();
            } catch (Exception e) {
                System.out.println("释放锁异常");
                e.printStackTrace();
            }
        }
        return num;
    }
}
