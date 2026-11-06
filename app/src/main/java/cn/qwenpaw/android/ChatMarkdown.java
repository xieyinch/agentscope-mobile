package cn.qwenpaw.android;
import io.github.xieyinch.agentscope.R;
import io.github.xieyinch.agentscope.BuildConfig;

import io.noties.markwon.*;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/** Shared renderer for chat messages and attachment previews. */
final class ChatMarkdown {
    private ChatMarkdown() {}
    static Markwon create(ConsoleFragment activity) {
        return Markwon.builder(activity.requireContext()).usePlugin(TablePlugin.create(activity.requireContext()))
            .usePlugin(StrikethroughPlugin.create()).usePlugin(TaskListPlugin.create(activity.requireContext()))
            .usePlugin(new AbstractMarkwonPlugin(){
                @Override public void configureConfiguration(MarkwonConfiguration.Builder builder){
                    builder.linkResolver((view,link)->activity.openLink(link));
                }
            }).build();
    }
}
