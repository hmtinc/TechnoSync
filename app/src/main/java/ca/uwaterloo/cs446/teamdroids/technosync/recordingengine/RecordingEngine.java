package ca.uwaterloo.cs446.teamdroids.technosync.recordingengine;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ca.uwaterloo.cs446.teamdroids.technosync.api.WebApi;
import ca.uwaterloo.cs446.teamdroids.technosync.common.StateArray;
import ca.uwaterloo.cs446.teamdroids.technosync.common.Tile;
import ca.uwaterloo.cs446.teamdroids.technosync.common.TileList;
import ca.uwaterloo.cs446.teamdroids.technosync.eventbus.EventPackage;
import ca.uwaterloo.cs446.teamdroids.technosync.eventbus.EventType;
import ca.uwaterloo.cs446.teamdroids.technosync.eventbus.Subscriber;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RecordingEngine extends Subscriber {

    CurrentState currentState;
    RecordingList recordingList;
    WebApi webApi;
    boolean recording = false;
    String recordingStartTime, recordingEndTime;
    String groupId = "";

    //Record changes to instrument pad
    public void instrumentPadUpdate(Tile tile) {
        if (!recording) return;
        RecordingEntry recordingEntry = new RecordingEntry(tile.getFileString(), false);
        recordingList.newEntry(recordingEntry);
    }


    //Record changes playing beats
    public void loopPadUpdate(StateArray stateArray) {
        if (!recording) return;

        //Get hit tile value
        Integer changedTile = currentState.determineChange(stateArray);
        String fileString = currentState.tileIdToFileName(changedTile, true);

        //No chaange, so don't record
        if (changedTile == -1) {
            return;
        }

        //Update current state
        currentState.setStateArray(stateArray);

        //Record value
        recordingList.newEntry(new RecordingEntry(fileString, true));
    }

    //Notify server that client is recording
    private void startRecording() {
        recording = true;
        recordingStartTime = new SimpleDateFormat("%D:%H:%M:%S.%f", Locale.CANADA).format(new Date());

        Call<RecordingList> call = webApi.getTechnoSyncService().startRecording(groupId);
        call.enqueue(new Callback<RecordingList>() {
            @Override
            public void onResponse(Call<RecordingList> call, Response<RecordingList> response) {
                if (response.isSuccessful()) {
                    Log.i("TechnoSynch", "Call succeeded");
                } else {
                    Log.i("TechnoSynch", "Got an error response, maybe like a 403");
                }
            }

            @Override
            public void onFailure(Call<RecordingList> call, Throwable t) {
                Log.i("TechnoSynch", "Actual http call failed (no internet, wrong url, etc.");
            }
        });
    }

    //Send local recording to server
    private void sendRecording() {
        recording = false;
        recordingEndTime = new SimpleDateFormat("%D:%H:%M:%S.%f", Locale.CANADA).format(new Date());

        //Upload
        Call<RecordingList> call = webApi.getTechnoSyncService().stopRecording(groupId);
        call.enqueue(new Callback<RecordingList>() {
            @Override
            public void onResponse(Call<RecordingList> call, Response<RecordingList> response) {
                if (response.isSuccessful()) {
                    Log.i("TechnoSynch", "Call succeeded");
                } else {
                    Log.i("TechnoSynch", "Got an error response, maybe like a 403");
                }
            }

            @Override
            public void onFailure(Call<RecordingList> call, Throwable t) {
                Log.i("TechnoSynch", "Actual http call failed (no internet, wrong url, etc.");
            }
        });
    }

    //Receive event from event bus
    public void notify(EventPackage eventPackage) {
        try {
            EventType eventType = eventPackage.getEventType();


            //Handle events with no data
            //Start recording
            if (eventType == EventType.RECORDING_START) {
                startRecording();
                return;
            }
            //Stop Recording
            else if (eventType == EventType.RECORDING_END) {
                sendRecording();
                return;
            }

            //Get stream of event data
            byte bytes[] = Base64.decode(eventPackage.getSerializedData().getBytes(), 0);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);


            //Update collections of loops
            if (eventType == EventType.LOOPPAD_MAPPING_UPDATE) {
                TileList tileList = (TileList) objectInputStream.readObject();
                currentState.setLoopList(tileList);
            }
            //Update collections of instruments
            else if (eventType == EventType.INSTRUMENTPAD_MAPPING_UPDATE) {
                TileList tileList = (TileList) objectInputStream.readObject();
                currentState.setInstrumentList(tileList);
            }
            //Update instrument playback
            else if (eventType == EventType.INSTRUMENTPAD_SOUND_HIT) {
                Tile tile = (Tile) objectInputStream.readObject();
                instrumentPadUpdate(tile);

            }
            //Update loop playback
            else if (eventType == EventType.LOOPPAD_STATE_UPDATE) {
                StateArray stateArray = (StateArray) objectInputStream.readObject();
                loopPadUpdate(stateArray);
            }

        } catch (Exception e) {
            //Error
            // TODO needs logger.
        }
    }

    //Get recording status
    public boolean isRecording() {
        return recording;
    }


    //Initialize
    public RecordingEngine(WebApi webApi, String groupId) {
        this.currentState = new CurrentState();
        this.recordingList = new RecordingList();
        this.webApi = webApi;
        this.groupId = groupId;
    }


}

