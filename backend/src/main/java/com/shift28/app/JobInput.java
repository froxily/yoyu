package com.shift28.app;

public record JobInput(String name, int hourlyWageYen, int transportYen, String color) {

	public static final String DEFAULT_COLOR = "#3b82f6";

	public static final int DEFAULT_TRANSPORT_YEN = 0;

	public static JobInput withDefaults(String name, int hourlyWageYen, Integer transportYen, String color) {
		return new JobInput(name, hourlyWageYen, transportYen == null ? DEFAULT_TRANSPORT_YEN : transportYen,
				color == null ? DEFAULT_COLOR : color);
	}

}
