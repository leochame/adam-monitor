package com.adam.ntp.network;

import com.adam.exception.NtpException;
import com.adam.exception.StratumViolationException;
import org.apache.commons.net.ntp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 协议校验器（网页9的协议规范实现）
public class NtpProtocolValidator {
    private static final Logger log = LoggerFactory.getLogger(NtpProtocolValidator.class);

    public static void validate(NtpV3Packet packet) {
        // 校验stratum层级
        if(packet.getStratum() > 5) {
            NtpProtocolValidator.log.warn("Stratum {} exceeds threshold", packet.getStratum());
        }

        // 校验Kiss-o'-Death报文
        if(packet.getReferenceIdString().startsWith("RATE")) {
            throw new NtpException("Server rate limited", null);
        }
    }
}

