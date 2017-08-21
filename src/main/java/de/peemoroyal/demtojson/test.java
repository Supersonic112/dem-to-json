/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.peemoroyal.demtojson;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import skadistats.clarity.Clarity;
import skadistats.clarity.decoder.Util;
import skadistats.clarity.model.Entity;
import skadistats.clarity.model.FieldPath;
import skadistats.clarity.processor.entities.*;
import skadistats.clarity.processor.runner.Context;
import skadistats.clarity.processor.runner.ControllableRunner;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.wire.common.proto.Demo;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import skadistats.clarity.event.Initializer;
import skadistats.clarity.event.Provides;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.model.GameEvent;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.reader.OnMessage;
import skadistats.clarity.processor.reader.OnTickStart;
import skadistats.clarity.wire.common.proto.NetMessages;
import skadistats.clarity.wire.common.proto.NetworkBaseTypes;
import skadistats.clarity.wire.s2.proto.S2DotaGcCommon;
import skadistats.clarity.wire.s2.proto.S2GameEvents;
import skadistats.clarity.wire.s2.proto.S2UserMessages;
/**
 *
 * @author petergleixner
 */
public class test {
    
    int time = 0 ;
    private SimpleRunner s_runner;
    
    public static void main(String[] args) throws Exception {
        new test().run(args);
    }
    public void run(String[] args) throws Exception{
        long tStart = System.currentTimeMillis();
        String source_path = System.getProperty("user.dir") + "/../Replays/__raw_replays/rep_s02_03.dem";
        MappedFileSource mfs = new MappedFileSource(source_path);  
        this.s_runner = new SimpleRunner(mfs);

        Demo.CDemoFileInfo info = Clarity.infoForFile(source_path);

        s_runner.runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;
        System.out.println("Time taken: " + tMatch);
    }
    @OnMessage(S2UserMessages.CUserMessageSayText2.class)
    public void onMessage(Context ctx, S2UserMessages.CUserMessageSayText2 message) {        System.out.format("%s: %s\n", message.getParam1(), message.getParam2());
    }

}