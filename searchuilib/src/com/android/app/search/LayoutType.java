/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.app.search;

/**
 * Constants to be used with {@link SearchTarget}.
 */
public class LayoutType {

    //     ------
    //    | icon |
    //     ------
    //      text
    //
    public static final String ICON_SINGLE_VERTICAL_TEXT = "icon";

    //     ------
    //    | icon | text
    //     ------
    public static final String ICON_SINGLE_HORIZONTAL_TEXT = "icon_text_row";

    //     ------               ------   ------
    //    | icon | text        | icon | | icon |
    //     ------               ------   ------
    public static final String ICON_INLINE_ICONS = "icon_text_icons";

    //     ------
    //    |      | text1
    //    | icon | text2
    //     ------
    public static final String ICON_DOUBLE_HORIZONTAL_TEXT = "icon_texts_row";

    // TODO: add diagram
    public static final String ICON_DOUBLE_HORIZONTAL_TEXT_BUTTON =
        "icon_texts_button";


    public static final String THUMBNAIL = "thumbnail";
    // TODO: add diagram
    public static final String ICON_SLICE = "slice";

    // TODO: add diagram
    public static final String TEXT_HEADER = "header";


    // Widget bitmap preview
    public static final String WIDGET_PREVIEW = "widget_preview";

    // Live widget search result
    public static final String WIDGET_LIVE = "widget_live";

    // TODO: replace the plugin item types with these string constants
}
