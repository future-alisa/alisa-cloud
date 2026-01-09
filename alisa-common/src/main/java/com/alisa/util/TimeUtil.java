package com.alisa.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时间处理工具
 */
public class TimeUtil {

    public static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_PATTERN);

    /** 默认格式解析 */
    public static LocalDateTime parse(String text) {
        return text == null ? null : LocalDateTime.parse(text, DEFAULT_FORMATTER);
    }

    /**
     * 灵活格式解析：
     * 2024/01/01
     */
    public static LocalDateTime parse(String text, String pattern) {
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 默认格式化
     * yyyy-MM-dd HH:mm:ss
     */
    public static String format(LocalDateTime time) {
        return time == null ? "" : time.format(DEFAULT_FORMATTER);
    }

    /** 毫秒时间戳转时间 */
    public static LocalDateTime ofMilli(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    /** 转为毫秒时间戳 */
    public static long toMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * * 增减时间
     */
    public static LocalDateTime plus(LocalDateTime time, long amount, ChronoUnit unit) {
        return time.plus(amount, unit);
    }

    /** 计算差值：返回相差的完整单位数 */
    public static long diff(LocalDateTime start, LocalDateTime end, ChronoUnit unit) {
        return unit.between(start, end);
    }

    /** 判断是否在范围内 */
    public static boolean isBetween(LocalDateTime target, LocalDateTime start, LocalDateTime end) {
        return target.isAfter(start) && target.isBefore(end);
    }

    /** 判断是否是今天 */
    public static boolean isToday(LocalDateTime time) {
        return time.toLocalDate().equals(LocalDate.now());
    }
}