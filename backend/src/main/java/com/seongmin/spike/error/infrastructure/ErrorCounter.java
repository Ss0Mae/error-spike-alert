package com.seongmin.spike.error.infrastructure;

public interface ErrorCounter {
    /** 이번 이벤트를 포함한 윈도우 내 건수를 돌려준다. 이벤트는 이미 MySQL에 커밋되어 있어야 한다. */
    CountResult increment(CounterRequest request);
}
