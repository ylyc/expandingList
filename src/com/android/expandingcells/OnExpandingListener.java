/*
 * Copyright (C) 2013 Leon Cheng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.expandingcells;

import android.view.View;

public interface OnExpandingListener {
    public void onExpand(View view);
    public void onCollapse(View view);
    public void onExpanded(View view);
    public void onCollasped(View view);
}
