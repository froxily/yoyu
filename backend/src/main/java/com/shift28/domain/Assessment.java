package com.shift28.domain;

public record Assessment(Attribution attribution, long minutes, int limitMinutes, Status status) {
}
