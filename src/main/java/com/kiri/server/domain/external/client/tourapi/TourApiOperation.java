package com.kiri.server.domain.external.client.tourapi;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TourApiOperation {

    AREA_BASED_SYNC_LIST("/areaBasedSyncList2"),
    DETAIL_COMMON("/detailCommon2"),
    DETAIL_INTRO("/detailIntro2"),
    DETAIL_INFO("/detailInfo2");

    private final String path;
}
