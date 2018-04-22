package com.list.beezlist;

import android.support.v7.widget.RecyclerView;

/**
 * Created by SHKEFATI on 22/04/2018.
 */

public interface RecyclerItemTouchHelperListener {
    void onSwiped(RecyclerView.ViewHolder viewHolder, int direction);

}
