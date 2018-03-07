package com.termux.api.util;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import java.io.BufferedOutputStream;
import java.io.InputStream;



/**
 * Created by eskender on 1/5/18.
 */

public class ConnectionEstablisher {

    /**
     * An extra intent parameter which specifies a linux abstract namespace socket address where output from the API
     * call should be written.
     */
    private final String SOCKET_OUTPUT_EXTRA = "socket_output";

    /**
     * An extra intent parameter which specifies a linux abstract namespace socket address where input to the API call
     * can be read from.
     */
    private final String SOCKET_INPUT_EXTRA = "socket_input";


    public abstract class Transmitter {

        public abstract void transmit(BufferedOutputStream bOutputStream);
    }

    public abstract class TransmitterReceiver extends Transmitter{

          public  abstract void receive(InputStream inputStream);
    }

    public abstract class MyRunnable implements Runnable {

        Looper mLooper;

    }



   public void connectionEstablish(Object context, final Intent intent, final Transmitter transmitter) {
       final BroadcastReceiver.PendingResult asyncResult = (context instanceof BroadcastReceiver) ? ((BroadcastReceiver) context)
               .goAsync() : null;
       final Activity activity = (Activity) ((context instanceof Activity) ? context : null);

       try {

           LocalSocket outputSocket = new LocalSocket();
           String outputSocketAdress = intent.getStringExtra(SOCKET_OUTPUT_EXTRA);
           outputSocket.connect(new LocalSocketAddress(outputSocketAdress));

           final BufferedOutputStream bOutputStream = new BufferedOutputStream(outputSocket.getOutputStream(), 320*240*4);

           LocalSocket inputSocket;
           final InputStream inputStream;


           HandlerThread receiverThread;
           MyRunnable RRunnable = null;
           Handler receiveHandler = null;

           if (transmitter != null) {
               if (transmitter instanceof TransmitterReceiver) {
                   inputSocket = new LocalSocket();
                   String inputSocketAdress = intent.getStringExtra(SOCKET_INPUT_EXTRA);
                   inputSocket.connect(new LocalSocketAddress(inputSocketAdress));

                   inputStream = inputSocket.getInputStream();

                   RRunnable = new MyRunnable() {
                       @Override
                       public void run() {

                           this.mLooper = Looper.myLooper();

                           TermuxApiLogger.info("Receiver started.");
                           ((TransmitterReceiver) transmitter).receive(inputStream);
                           TermuxApiLogger.info("Receiver stopped");
                       }
                   };
                   receiverThread = new HandlerThread("Receiver");
                   receiverThread.start();

                   receiveHandler = new Handler(receiverThread.getLooper());
                   receiveHandler.post(RRunnable);
               }
           }

           final MyRunnable TRunnable;
           TRunnable = new MyRunnable() {
               @Override
               public void run() {

                   this.mLooper = Looper.myLooper();

                   TermuxApiLogger.info("Transmitter started.");
                   transmitter.transmit(bOutputStream);
                   TermuxApiLogger.info("Transmitter stopped");

               }
           };


           HandlerThread transmitterThread = new HandlerThread("Transmitter");
           transmitterThread.start();

           Handler transmitHandler = new Handler(transmitterThread.getLooper());
           transmitHandler.post(TRunnable);


           if (asyncResult != null) {
               asyncResult.setResultCode(0);
           } else if (activity != null) {
               activity.setResult(0);
           }


           while (true) {
               if (transmitter instanceof TransmitterReceiver) {
                   if (!receiveHandler.sendEmptyMessage(0)) {
                       TermuxApiLogger.info("Receiver thread died");
                       TermuxApiLogger.info("Killing transmitter thread");
                       TRunnable.mLooper.quit();
                       break;
                   }
                   else if (!transmitHandler.sendEmptyMessage(0)) {
                       TermuxApiLogger.info("Transmitter thread died");
                       TermuxApiLogger.info("Killing receiver thread");
                       RRunnable.mLooper.quit();
                       break;
                   }
               }
               else if (!transmitHandler.sendEmptyMessage(0)) {
                       TermuxApiLogger.info("Transmitter thread died");
                       break;
               }

               try{
                   Thread.sleep(1000);
                   TermuxApiLogger.info("Service running");
               }catch(Exception e){
                   TermuxApiLogger.error("Error while Thread.sleep()", e);
               }
           }

           //Getting here means connection is lost due to some error.
           TermuxApiLogger.info("Connection lost.");


       } catch (Exception e) {
           TermuxApiLogger.error("Error in ConnectionEstablisher", e);

           if (asyncResult != null) {
               asyncResult.setResultCode(1);
           } else if (activity != null) {
               activity.setResult(1);
           }
       } finally {
           if (asyncResult != null) {
               asyncResult.finish();
           } else if (activity != null) {
               activity.finish();
           }
       }

    }
}

