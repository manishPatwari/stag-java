package com.vimeo.sample.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class IdenticalFieldTypes {

    @SerializedName("mUser")
    User mUser;

    @SerializedName("mSecondUser")
    User mSecondUser;

    @SerializedName("mUsersList")
    ArrayList<User> mUsersList;

    @SerializedName("mSecondUsersList")
    ArrayList<User> mSecondUsersList;

    @SerializedName("mStatsArrayList")
    ArrayList<Stats> mStatsArrayList;

    @SerializedName("mStats")
    Stats mStats;
}
