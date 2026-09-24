package com.tsukieru.lagtop;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public final class CpuMonitor {
    private final OperatingSystemMXBean bean;

    public CpuMonitor() {
        this.bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    }

    public double getProcessCpuPercent() {
        double value = bean == null ? -1.0 : bean.getProcessCpuLoad();
        return value < 0 ? -1.0 : value * 100.0;
    }

    public double getSystemCpuPercent() {
        double value = bean == null ? -1.0 : bean.getCpuLoad();
        return value < 0 ? -1.0 : value * 100.0;
    }
}
