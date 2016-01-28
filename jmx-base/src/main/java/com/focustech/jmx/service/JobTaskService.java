package com.focustech.jmx.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;

import com.focustech.jmx.DAO.JmxTaskScheduleJobDAO;
import com.focustech.jmx.po.JmxTaskScheduleJob;
import com.focustech.jmx.quartz.QuartzJobFactory;
import com.focustech.jmx.quartz.QuartzJobFactoryDisallowConcurrentExecution;

/**
 * 计划任务管理
 * 
 * @author wangwei-ww
 */
@Service
public class JobTaskService extends AbstractService {

    @Autowired
    private SchedulerFactoryBean schedulerFactoryBean;

    @Autowired
    private JmxTaskScheduleJobDAO scheduleJobMapper;

    /**
     * 从数据库中取 区别于getAllJob
     * 
     * @return
     */
    public List<JmxTaskScheduleJob> getAllTask() {
        return scheduleJobMapper.getAll();
    }

    public int updateByPrimaryKeySelective(JmxTaskScheduleJob record) {
        return scheduleJobMapper.updateByPrimaryKeySelective(record);
    }

    /**
     * 添加到数据库中 区别于addJob
     */
    public void addTask(JmxTaskScheduleJob job) {
        job.setCreateTime(new Date());
        scheduleJobMapper.insertSelective(job);
    }

    /**
     * 从数据库中查询job
     */
    public JmxTaskScheduleJob getTaskById(Long jobId) {
        return scheduleJobMapper.selectByPrimaryKey(jobId);
    }

    /**
     * 更改任务状态
     * 
     * @throws SchedulerException
     */
    public void changeStatus(Long jobId, String cmd) throws SchedulerException {
        JmxTaskScheduleJob job = getTaskById(jobId);
        if (job == null) {
            return;
        }
        if ("stop".equals(cmd)) {
            deleteJob(job);
            job.setJobStatus(JmxTaskScheduleJob.STATUS_NOT_RUNNING);
        }
        else if ("start".equals(cmd)) {
            job.setJobStatus(JmxTaskScheduleJob.STATUS_RUNNING);
            addJob(job);
        }
        scheduleJobMapper.updateByPrimaryKeySelective(job);
    }

    /**
     * 更改任务 cron表达式
     * 
     * @throws SchedulerException
     */
    public void updateCron(Long jobId, String cron) throws SchedulerException {
        JmxTaskScheduleJob job = getTaskById(jobId);
        if (job == null) {
            return;
        }
        job.setCronExpression(cron);
        if (JmxTaskScheduleJob.STATUS_RUNNING.equals(job.getJobStatus())) {
            updateJobCron(job);
        }

    }

    /**
     * 添加任务
     * 
     * @param scheduleJob
     * @throws SchedulerException
     */
    public void addJob(JmxTaskScheduleJob job) throws SchedulerException {
        if (job == null || !JmxTaskScheduleJob.STATUS_RUNNING.equals(job.getJobStatus())) {
            return;
        }

        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        log.error(scheduler
                + ".......................................................................................add");
        TriggerKey triggerKey = TriggerKey.triggerKey(job.getJobName(), job.getJobGroup());

        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);

        // 不存在，创建一个
        if (null == trigger) {
            Class<? extends Job> clazz =
                    JmxTaskScheduleJob.CONCURRENT_IS.equals(job.getIsConcurrent()) ? QuartzJobFactory.class
                            : QuartzJobFactoryDisallowConcurrentExecution.class;

            JobDetail jobDetail = JobBuilder.newJob(clazz).withIdentity(job.getJobName(), job.getJobGroup()).build();

            jobDetail.getJobDataMap().put("scheduleJob", job);

            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(job.getCronExpression());

            trigger =
                    TriggerBuilder.newTrigger().withIdentity(job.getJobName(), job.getJobGroup())
                            .withSchedule(scheduleBuilder).build();

            scheduler.scheduleJob(jobDetail, trigger);

        }
        else {
            // Trigger已存在，那么更新相应的定时设置
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(job.getCronExpression());

            // 按新的cronExpression表达式重新构建trigger
            trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();

            // 按新的trigger重新设置job执行
            scheduler.rescheduleJob(triggerKey, trigger);
        }
    }

    /**
     * 获取所有计划中的任务列表
     * 
     * @return
     * @throws SchedulerException
     */
    public List<JmxTaskScheduleJob> getAllJob() throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        GroupMatcher<JobKey> matcher = GroupMatcher.anyJobGroup();
        Set<JobKey> jobKeys = scheduler.getJobKeys(matcher);
        List<JmxTaskScheduleJob> jobList = new ArrayList<JmxTaskScheduleJob>();
        for (JobKey jobKey : jobKeys) {
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            for (Trigger trigger : triggers) {
                JmxTaskScheduleJob job = new JmxTaskScheduleJob();
                job.setJobName(jobKey.getName());
                job.setJobGroup(jobKey.getGroup());
                job.setDescription("触发器:" + trigger.getKey());
                Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
                job.setJobStatus(triggerState.name());
                if (trigger instanceof CronTrigger) {
                    CronTrigger cronTrigger = (CronTrigger) trigger;
                    String cronExpression = cronTrigger.getCronExpression();
                    job.setCronExpression(cronExpression);
                }
                jobList.add(job);
            }
        }
        return jobList;
    }

    /**
     * 所有正在运行的job
     * 
     * @return
     * @throws SchedulerException
     */
    public List<JmxTaskScheduleJob> getRunningJob() throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        List<JobExecutionContext> executingJobs = scheduler.getCurrentlyExecutingJobs();
        List<JmxTaskScheduleJob> jobList = new ArrayList<JmxTaskScheduleJob>(executingJobs.size());
        for (JobExecutionContext executingJob : executingJobs) {
            JmxTaskScheduleJob job = new JmxTaskScheduleJob();
            JobDetail jobDetail = executingJob.getJobDetail();
            JobKey jobKey = jobDetail.getKey();
            Trigger trigger = executingJob.getTrigger();
            job.setJobName(jobKey.getName());
            job.setJobGroup(jobKey.getGroup());
            job.setDescription("触发器:" + trigger.getKey());
            Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
            job.setJobStatus(triggerState.name());
            if (trigger instanceof CronTrigger) {
                CronTrigger cronTrigger = (CronTrigger) trigger;
                String cronExpression = cronTrigger.getCronExpression();
                job.setCronExpression(cronExpression);
            }
            jobList.add(job);
        }
        return jobList;
    }

    /**
     * 暂停一个job
     * 
     * @param scheduleJob
     * @throws SchedulerException
     */
    public void pauseJob(JmxTaskScheduleJob scheduleJob) throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        JobKey jobKey = JobKey.jobKey(scheduleJob.getJobName(), scheduleJob.getJobGroup());
        scheduler.pauseJob(jobKey);
    }

    /**
     * 恢复一个job
     * 
     * @param scheduleJob
     * @throws SchedulerException
     */
    public void resumeJob(JmxTaskScheduleJob scheduleJob) throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        JobKey jobKey = JobKey.jobKey(scheduleJob.getJobName(), scheduleJob.getJobGroup());
        scheduler.resumeJob(jobKey);
    }

    /**
     * 删除一个job
     * 
     * @param scheduleJob
     * @throws SchedulerException
     */
    public void deleteJob(JmxTaskScheduleJob scheduleJob) throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        JobKey jobKey = JobKey.jobKey(scheduleJob.getJobName(), scheduleJob.getJobGroup());
        scheduler.deleteJob(jobKey);

    }

    public void reloadJob(JmxTaskScheduleJob newJob, JmxTaskScheduleJob orginJob) throws SchedulerException {

        deleteJob(orginJob);
        addJob(newJob);

    }

    /**
     * 立即执行job
     * 
     * @param scheduleJob
     * @throws SchedulerException
     */
    public void runAJobNow(JmxTaskScheduleJob scheduleJob) throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();
        JobKey jobKey = JobKey.jobKey(scheduleJob.getJobName(), scheduleJob.getJobGroup());
        scheduler.triggerJob(jobKey);
    }

    /**
     * 更新job时间表达式
     * 
     * @param scheduleJob
     * @throws SchedulerException
     */
    public void updateJobCron(JmxTaskScheduleJob scheduleJob) throws SchedulerException {
        Scheduler scheduler = schedulerFactoryBean.getScheduler();

        TriggerKey triggerKey = TriggerKey.triggerKey(scheduleJob.getJobName(), scheduleJob.getJobGroup());

        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);

        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression());

        trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();

        scheduler.rescheduleJob(triggerKey, trigger);
    }

    public SchedulerFactoryBean getSchedulerFactoryBean() {
        return schedulerFactoryBean;
    }

    public JmxTaskScheduleJobDAO getScheduleJobMapper() {
        return scheduleJobMapper;
    }

    public boolean isNeedReloadJob(JmxTaskScheduleJob newJob, JmxTaskScheduleJob orginJob) {
        if (newJob == null || orginJob == null) {
            return false;
        }
        if (!StringUtils.equals(newJob.getJobGroup(), orginJob.getJobGroup())
                || !StringUtils.equals(newJob.getJobName(), orginJob.getJobName())
                || !StringUtils.equals(newJob.getSpringId(), orginJob.getSpringId())
                || !StringUtils.equals(newJob.getMethodName(), orginJob.getMethodName())) {
            return true;
        }
        return false;
    }

}
