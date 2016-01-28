package com.focustech.jmx.guice;

import javax.inject.Inject;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Injector;

public class GuiceJobFactory implements JobFactory {

    private final Injector injector;

    @Inject
    public GuiceJobFactory(Injector injector) {
        this.injector = injector;
    }

    public Job newJob(TriggerFiredBundle bundle, Scheduler arg1) throws SchedulerException {
        JobDetail jobDetail = bundle.getJobDetail();
        Class<Job> jobClass = (Class<Job>) jobDetail.getJobClass();
        return injector.getInstance(jobClass);
    }
}
