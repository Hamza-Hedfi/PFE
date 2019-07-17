package com.double_h.padvpad.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.double_h.padvpad.R;
import com.double_h.padvpad.api.models.Classification;
import com.double_h.padvpad.api.models.Speech;
import com.double_h.padvpad.api.service.PADVPASRestAPIClient;
import com.double_h.padvpad.api.service.ServiceBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    protected static final String TAG = SpeechRecognizer.class.getSimpleName();
    protected static Decoder sDecoder;
    protected static File sAssetsDir;

    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private static boolean sRecording = false;
    String mOutputFileString = null;
    String mWavOutputFileString = null;
    private static String mFileName;
    private static File sOutputFile;
    int mBufferSize;
    private AudioRecord mRecorder;
    private static byte[] sBuffer;
    Thread mRecordingThread;
    File wavOutputFile = null;
    ImageView valid_or_not;

    PADVPASRestAPIClient taskService;

    Speech speech;

    TextView caption_text;

    static {
        System.loadLibrary("pocketsphinx_jni");
    }

    private static class SetupTask extends AsyncTask<Void, Void, String> {
        WeakReference<MainActivity> activityReference;

        SetupTask(MainActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(Void... params) {
            Assets assets = null;
            try {
                assets = new Assets(activityReference.get());
                sAssetsDir = assets.syncAssets();
                Config c = Decoder.defaultConfig();
                c.setString("-hmm", new File(sAssetsDir, "pfe_accoustic_model").getPath());
                c.setString("-dict", new File(sAssetsDir, "pfe_accoustic_model.dict").getPath());
                c.setBoolean("-allphone_ci", true);
                c.setFloat("-kws_threshold", 1e-45f);
                c.setString("-lm", new File(sAssetsDir, "pfe_accoustic_model.lm.DMP").getPath());
                sDecoder = new Decoder(c);
                Log.i(TAG, "Done.....");
                return "Recognizer Setup Complete.";

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            ((TextView) activityReference.get().findViewById(R.id.caption_text)).setText(result + "\n Hit the action button and start speaking.");
        }
    }

    private void audioRecord(View view) {
        final View v = view;

        //final AudioRecord mRecorder;
        if (!sRecording) {
            //Get unique timestamp for file naming
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
            Date now = new Date();
            String uniqueFileID = formatter.format(now);

            //Give the matching raw and wav file the same unique ID, just with different file types.
            mOutputFileString = String.format("%s%s.raw", mFileName, uniqueFileID);
            mWavOutputFileString = String.format("%s%s.wav", mFileName, uniqueFileID);
            Log.i(TAG, mOutputFileString);
            Log.d(TAG, mWavOutputFileString);

            sOutputFile = new File(mOutputFileString);
            int sampleRate = (int) this.sDecoder.getConfig().getFloat("-samprate");
            mBufferSize = Math.round((float) sampleRate * 0.4F);
            mRecorder = new AudioRecord(6, sampleRate, 16, 2, mBufferSize * 2);
            if (mRecorder.getState() == 0)
                this.mRecorder.release();

            sBuffer = new byte[mBufferSize];
        }
        //Thread is created to check whether user is sRecording or not and starts/ends the sRecording
        mRecordingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                //if user is NOT sRecording, begin sRecording and write audio sBuffer to the output file
                if (!sRecording) {
                    sRecording = true;
                    //Signals mRecorder object to open mic and begin recoding
                    mRecorder.startRecording();
                    FileOutputStream fos = null;
                    try {
                        //opens output file
                        fos = new FileOutputStream(sOutputFile.getPath());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    int read = 0;
                    //will continue to write audio sBuffer to file until recoding is ended by user
                    while (sRecording) {
                        read = mRecorder.read(sBuffer, 0, sBuffer.length);
                        Log.i(TAG, "FINISHED BUFFER");
                        try {
                            assert fos != null;
                            //once sBuffer is full write it to the output file
                            fos.write(sBuffer);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    //close output file
                    try {
                        assert fos != null;
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //once done the thread is block and waits to be terminated by parent process
                    try {
                        mRecordingThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                //if the user IS sRecording, stop sRecording close output file and convert raw file
                //into a file of .wav format
                else {
                    sRecording = false;
                    //signals mRecorder to end sRecording and mRecorder object is released
                    mRecorder.stop();
                    mRecorder.release();
                    mRecorder = null;
                    if (mWavOutputFileString != null) {
                        wavOutputFile = new File(mWavOutputFileString);
                        //convert resulting .raw file into .wav format of the same name
                        rawToWav(sOutputFile.getAbsolutePath(), wavOutputFile.getAbsolutePath());
                        Log.i(TAG, wavOutputFile.getAbsolutePath());
                        //final .wav file is passed into convertToSpeech wher the text will be extracted
                        convertVoiceToSpeech(v, sDecoder, sAssetsDir);
                        sBuffer = null;
                    }
                    //once done the thread is block and waits to be terminated by parent process
                    try {
                        mRecordingThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, "Recorder Thread");

        //thread is started
        mRecordingThread.start();

    }


    private void convertVoiceToSpeech(View v, final Decoder d, final File assetsDir) {
        //We create a new asynchronous task to asynchronously process speech
        new AsyncTask<Void, Void, String>() {

            //The process of actually parsing the text from the voice file is done in background, as to
            //not significantly slow down the app
            @Override
            protected String doInBackground(Void... params) {

                String output = null; //Our result string.
                Log.i(TAG, assetsDir.getAbsolutePath()); //For the purposes of debugging
                InputStream stream = null; //Input stream that will be used to get contents out of recorded file

                try {
                    stream = new FileInputStream(wavOutputFile); //We take the stream from our wavOutputFile
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                d.startUtt(); //Tells Decoder to start the voice processing

                byte[] buf = new byte[4096]; //A buffer that will be used to hold chunks of our input file

                try {
                    int nbytes; //The number of bytes read into the buffer

                    assert stream != null; //Ensure that stream isn't null

                    //Reading process for the input file. Fills buffer, processes buffer and repeats
                    while ((nbytes = stream.read(buf)) >= 0) {

                        ByteBuffer bb = ByteBuffer.wrap(buf, 0, nbytes); //Instantiates a ByteBuffer, used to change byte order and create short array

                        bb.order(ByteOrder.LITTLE_ENDIAN); //Orders the bytes using Little endian notation (this is required on android)
                        short[] s = new short[nbytes / 2]; //Creates a new, empty short array
                        bb.asShortBuffer().get(s); //Takes the now Little endian sorted bytes and puts them in our recently created short array

                        d.processRaw(s, nbytes / 2, false, false); //Call the decoder to process out short array

                    }

                } catch (IOException e) {
                    // fail("Error when reading inputstream" + e.getMessage());
                    e.printStackTrace();
                }

                d.endUtt(); //Tells Decoder to end the voice processing

                String text = d.hyp().getHypstr(); //Gets the hypothesis(The decoder's guess at the utterance from the voice file)
                Log.i(TAG, d.hyp().getHypstr()); //For debugging purposes
                return text; //Returns our result string.
            }

            //This will be executed after the thread finishes executing. Takes the return of doInBackground as parameter
            protected void onPostExecute(String result) {
                valid_or_not.setVisibility(View.INVISIBLE);
                //If a result was obtained, adjust caption, and report the result string.
                if (result != null) {
                    // caption_text.setText("Extracted Text:");
                    ((TextView) findViewById(R.id.result_speech)).setText(result);


                    TextView speechText = findViewById(R.id.result_speech);
                    speech.setSpeech(speechText.getText().toString());
                    if (speechText.getText().toString().length() != 0) {
                        Call<Classification> call = taskService.classificationForSpeech(speech);

                        call.enqueue(new Callback<Classification>() {
                            @Override
                            public void onResponse(Call<Classification> call, Response<Classification> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(MainActivity.this, response.body().getClassification(), Toast.LENGTH_LONG).show();
                                    if (response.body().getClassification().equalsIgnoreCase("healthy")) {
                                        valid_or_not.setImageResource(R.drawable.ic_check_black_24dp);
                                        valid_or_not.setVisibility(View.VISIBLE);
                                    } else {
                                        valid_or_not.setImageResource(R.drawable.ic_close_black_24dp);
                                        valid_or_not.setVisibility(View.VISIBLE);
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<Classification> call, Throwable t) {
                                Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        Toast.makeText(MainActivity.this, "Try again, couldn't hear you.....", Toast.LENGTH_LONG).show();
                    }


                } else {

                    Toast.makeText(MainActivity.this, "Try again, couldn't hear you.....", Toast.LENGTH_LONG).show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void rawToWav(String inFilename, String outFilename) {
        FileInputStream in = null; //The input stream for the raw file
        FileOutputStream out = null; //The output stream for the wav file
        long audioLength = 0; //Initialization of our audio length (In reference to WAV format: Subchunk2Size)
        long dataLength = audioLength + 36; //Initialization of our data length (In reference to WAV format: SubchunkSize)
        long longSampleRate = 16000; //Our Sample rate, PocketSphinx requires 16khz for accuracy
        int numChannels = 1; //The number of channels used, PocketSphinx requires mono-sound
        long byteRate = 16 * 16000 * numChannels / 8; //Initialization of our byteRate (For byteRate in WAV header)

        //Creates new buffer that will be used to transfer data from raw file to wav file
        byte[] data = new byte[mBufferSize];
        try {
            in = new FileInputStream(inFilename); //Assign our raw Inputstream to the inputStream prev. declared
            out = new FileOutputStream(outFilename); //Assign our wav Outputstream to the outputStream prev. declared
            audioLength = in.getChannel().size(); //Obtains the audio length(basically the size of the raw file)
            dataLength = audioLength + 36; //Obtains what will be the chunksize of the wav file (raw length + length of wav header)

            //Calls method that writes the header of a wav file with our previously defined wav header specifications
            WriteWaveFileHeader(out, audioLength, dataLength,
                    longSampleRate, numChannels, byteRate);
            //Note, after this method call, our output file now has the wav header with nothing else

            //Writes the data of the raw file to the wav file, creating a complete wav file (header + data)
            while (in.read(data) != -1) {
                out.write(data);
            }

            //Close streams
            in.close();
            out.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long audioLength, long dataLength,
                                     long longSampleRate, int channels, long byteRate)
            throws IOException {

        //The byte array that will temporarily hold the header
        byte[] header = new byte[44];

        header[0] = 'R'; //ChunkID
        header[1] = 'I'; //ChunkID
        header[2] = 'F'; //ChunkID
        header[3] = 'F'; //ChunkID
        header[4] = (byte) (dataLength & 0xff); //ChunkSize
        header[5] = (byte) ((dataLength >> 8) & 0xff); //ChunkSize
        header[6] = (byte) ((dataLength >> 16) & 0xff); //ChunkSize
        header[7] = (byte) ((dataLength >> 24) & 0xff); //ChunkSize
        header[8] = 'W'; //Format
        header[9] = 'A'; //Format
        header[10] = 'V'; //Format
        header[11] = 'E'; //Format
        header[12] = 'f'; //Subchunk1ID
        header[13] = 'm'; //Subchunk1ID
        header[14] = 't'; //Subchunk1ID
        header[15] = ' '; //Subchunk1ID
        header[16] = 16; //Subchunk1Size
        header[17] = 0; //Subchunk1Size
        header[18] = 0; //Subchunk1Size
        header[19] = 0; //Subchunk1Size
        header[20] = 1; //AudioFormat
        header[21] = 0; ////AudioFormat
        header[22] = (byte) channels; //NumChannels
        header[23] = 0; //NumChannels
        header[24] = (byte) (longSampleRate & 0xff); //SampleRate
        header[25] = (byte) ((longSampleRate >> 8) & 0xff); //SampleRate
        header[26] = (byte) ((longSampleRate >> 16) & 0xff); //SampleRate
        header[27] = (byte) ((longSampleRate >> 24) & 0xff); //SampleRate
        header[28] = (byte) (byteRate & 0xff); //ByteRate
        header[29] = (byte) ((byteRate >> 8) & 0xff); //ByteRate
        header[30] = (byte) ((byteRate >> 16) & 0xff); //ByteRate
        header[31] = (byte) ((byteRate >> 24) & 0xff); //ByteRate
        header[32] = (byte) (2 * 16 / 8); //BlockAlign
        header[33] = 0; //BlockAlign
        header[34] = 16; //BitsPerSamples
        header[35] = 0; //BitsPerSamples
        header[36] = 'd'; //Subchunk2ID
        header[37] = 'a'; //Subchunk2ID
        header[38] = 't'; //Subchunk2ID
        header[39] = 'a'; //Subchunk2ID
        header[40] = (byte) (audioLength & 0xff); //Subchunk2Size
        header[41] = (byte) ((audioLength >> 8) & 0xff); //Subchunk2Size
        header[42] = (byte) ((audioLength >> 16) & 0xff); //Subchunk2Size
        header[43] = (byte) ((audioLength >> 24) & 0xff); //Subchunk2Size

        //Writes our recently created header to the output file.
        out.write(header, 0, 44);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Toolbar toolbar = findViewById(R.id.toolbar);
        // setSupportActionBar(toolbar);

        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        }

        caption_text = findViewById(R.id.caption_text);

        caption_text.setText("Setting up the recognizer, one moment...");
        new SetupTask(this).execute();

        mFileName = "/storage/emulated/0/Android/data/com.double_h.padvpad/files/sync" + File.separator;

        valid_or_not = findViewById(R.id.imageView);
        taskService = ServiceBuilder.buildService(PADVPASRestAPIClient.class);
        speech = new Speech();


        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!sRecording) {
                    if (caption_text.getVisibility() == View.VISIBLE) {
                        caption_text.setVisibility(View.GONE);
                    }
                    audioRecord(view);
                    // caption_text.setText("End Recording");
                    Snackbar.make(view, "Speech recognition started!", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    ((FloatingActionButton) findViewById(R.id.fab)).setImageResource(R.drawable.baseline_voice_over_off_white_24);
                }
                //ends the recording process
                else {
                    audioRecord(view);
                    // caption_text.setText("Start Recording");
                    Snackbar.make(view, "Speech recognition stopped!", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    ((FloatingActionButton) findViewById(R.id.fab)).setImageResource(R.drawable.ic_record_voice_over_black_24dp);
                }
            }
        });
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }
}
