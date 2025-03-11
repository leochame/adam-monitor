package com.adam.test.ntp.core;

// 文件3: ClockCompensator.java
public class ClockCompensator {
    //累计时间偏移量
    private double timeOffset;
    // 时间漂移率（毫秒/毫秒，例如本地时钟每秒快/慢多少毫秒）
    private double timeDrift;
    // 误差协方差，表示对当前时间偏移估计的不确定性
    private double errorCovariance;
    private long lastUpdateTime = System.currentTimeMillis();
    // 过程噪声（系统模型的不确定性）
    private static final double PROCESS_NOISE = 1e-6;
    // 测量噪声（NTP时间测量的误差）
    private static final double MEASUREMENT_NOISE = 15_000;

    public synchronized long compensate(long ntpTime, long rtt) {

        long currentTime = System.currentTimeMillis();
        //计算当前时间与上次更新的时间差
        long deltaTime = currentTime - lastUpdateTime;
        //服务器时间与本地时间的理论偏差，也就是需要补偿的偏移量
        long measurement = ntpTime - currentTime - (rtt/2);

        predict(deltaTime);
        update(measurement);

        lastUpdateTime = currentTime;
        return currentTime + (long)timeOffset;
    }

    /**
     * 只基于过去时间漂移率预测未来的偏移量，
     * 并增加过程噪声（表示预测的不确定性）。
     */
    private void predict(double deltaTime) {
        timeOffset += timeDrift * deltaTime;
        errorCovariance += (deltaTime * deltaTime * PROCESS_NOISE);
    }

    /**
     * 结合测量值调整预测值
     * 通过卡尔曼增益平衡预测和测量的权重
     */
    private void update(double measurement) {
        double kalmanGain = errorCovariance / (errorCovariance + MEASUREMENT_NOISE);
        // 修正偏移量
        timeOffset += kalmanGain * (measurement - timeOffset);

        // 修正漂移率
        timeDrift += kalmanGain * ((measurement - timeOffset) / errorCovariance);
        // 降低不确定性
        errorCovariance *= (1 - kalmanGain);
    }
}