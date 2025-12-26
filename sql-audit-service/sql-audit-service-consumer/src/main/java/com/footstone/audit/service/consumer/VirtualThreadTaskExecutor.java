package com.footstone.audit.service.consumer;

import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * 虚拟线程任务执行器.
 *
 * <h2>功能说明</h2>
 * <p>基于JDK 21+的虚拟线程(Virtual Threads)实现的任务执行器</p>
 *
 * <h2>虚拟线程特性</h2>
 * <ul>
 *   <li>轻量级: 每个虚拟线程只占用KB级别内存，而平台线程需要MB级别</li>
 *   <li>高并发: 可以创建数百万个虚拟线程，不受平台线程数限制</li>
 *   <li>透明调度: JVM自动将虚拟线程映射到平台线程，无需手动管理线程池</li>
 * </ul>
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>IO密集型任务(如Kafka消息消费、HTTP调用、数据库查询)</li>
 *   <li>需要大量并发连接的场景</li>
 *   <li>希望简化异步编程，使用同步代码风格</li>
 * </ul>
 *
 * @see SimpleAsyncTaskExecutor
 * @since 2.0.0
 */
public class VirtualThreadTaskExecutor extends SimpleAsyncTaskExecutor {

    /**
     * 创建虚拟线程任务执行器.
     *
     * @param threadNamePrefix 线程名称前缀，用于日志和监控中识别线程来源
     */
    public VirtualThreadTaskExecutor(String threadNamePrefix) {
        super(threadNamePrefix);
        setVirtualThreads(true);
    }
}
