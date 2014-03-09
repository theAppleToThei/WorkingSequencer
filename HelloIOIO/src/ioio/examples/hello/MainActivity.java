package ioio.examples.hello;

/******************************************************************
 * VicsWagon Test Application with IOIO0503
 * Runs OK with single PWM frequency...adding new motor control
 * version 140308A
 * Copyright Wintriss Technical Schools 2014
 ******************************************************************/
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PulseInput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.Sequencer;
import ioio.lib.api.Sequencer.ChannelConfig;
import ioio.lib.api.Sequencer.ChannelConfigBinary;
import ioio.lib.api.Sequencer.ChannelConfigSteps;
import ioio.lib.api.Sequencer.ChannelCueBinary;
import ioio.lib.api.Sequencer.ChannelCueSteps;
import ioio.lib.api.Sequencer.Clock;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * This is the main activity of the HelloIOIO example application. It displays a
 * toggle button on the screen, which enables control of the on-board LED.
 */
public class MainActivity extends IOIOActivity
{
	private ToggleButton button_;
	private TextView log;
	private ScrollView scroller;
	private int frontDistance;
	private int rearDistance;
	private int leftDistance;
	private int rightDistance;
	private PwmOutput rightMotorClock;
	private PwmOutput leftMotorClock;
	private int pulseWidth = 10;// microseconds
	private PulseInput front;
	private PulseInput rear;
	private PulseInput left;
	private PulseInput right;
	private DigitalOutput rightMotorClockPulse;
	private DigitalOutput leftMotorClockPulse;
	private DigitalOutput frontStrobe;
	private DigitalOutput rearStrobe;
	private DigitalOutput leftStrobe;
	private DigitalOutput rightStrobe;
	private DigitalOutput rightMotorDirection;
	private DigitalOutput leftMotorDirection;
	private DigitalOutput halfFull;
	private DigitalOutput motorEnable; // Must be true for motors to run.
	private DigitalOutput reset; // Must be true for motors to run.
	private DigitalOutput control;// Decay mode selector high = slow, low = fast
	private DigitalOutput motorControllerControl;// Decay mode selector, high = slow decay, low = fast decay
	private static final int MOTOR_ENABLE_PIN = 3;// Low turns off all power to botyh motors
	private static final int MOTOR_RIGHT_DIRECTION_OUTPUT_PIN = 20;// High = clockwise, low = counter-clockwise
	private static final int MOTOR_LEFT_DIRECTION_OUTPUT_PIN = 21;
	private static final int MOTOR_CONTROLLER_CONTROL_PIN = 6;// For both motors
	private static final int REAR_STROBE_ULTRASONIC_OUTPUT_PIN = 14;// output from ioio board
	private static final int FRONT_STROBE_ULTRASONIC_OUTPUT_PIN = 16;
	private static final int LEFT_STROBE_ULTRASONIC_OUTPUT_PIN = 17;
	private static final int RIGHT_STROBE_ULTRASONIC_OUTPUT_PIN = 15;
	private static final int FRONT_ULTRASONIC_INPUT_PIN = 12;
	private static final int REAR_ULTRASONIC_INPUT_PIN = 10;// input to ioio board
	private static final int RIGHT_ULTRASONIC_INPUT_PIN = 11;
	private static final int LEFT_ULTRASONIC_INPUT_PIN = 13;
	private static final int MOTOR_HALF_FULL_STEP_PIN = 7;// For both motors
	private static final int MOTOR_RESET = 22;// For both motors
	private static final int MOTOR_CLOCK_LEFT_PIN = 27;
	private static final int MOTOR_CLOCK_RIGHT_PIN = 28;
	private int leftMotorPWMfrequency = 1000;
	private int rightMotorPWMfrequency = 1000;
	private DigitalOutput led_; // On-board led
	private Sequencer.ChannelCueBinary stepperDirCue_ = new ChannelCueBinary();
	private Sequencer.ChannelCueSteps stepperStepCue_ = new ChannelCueSteps();
	private Sequencer.ChannelCue[] cue_ = new Sequencer.ChannelCue[] {stepperDirCue_, stepperStepCue_};
	private Sequencer sequencer_;
	final ChannelConfigBinary stepperDirConfig = new Sequencer.ChannelConfigBinary(false, false,
			new DigitalOutput.Spec(MOTOR_RIGHT_DIRECTION_OUTPUT_PIN));
	final ChannelConfigSteps stepperStepConfig = new ChannelConfigSteps(new DigitalOutput.Spec(MOTOR_CLOCK_RIGHT_PIN));
	final ChannelConfig[] config = new ChannelConfig[] { stepperDirConfig, stepperStepConfig };

	/**
	 * Called when the activity is first created. Here we normally initialize
	 * our GUI.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		button_ = (ToggleButton) findViewById(R.id.button);
		log = (TextView) findViewById(R.id.log);
		scroller = (ScrollView) findViewById(R.id.log_scroller);
		log("onCreate");
		// log(getString(R.string.wait_ioio));
	}

	/**
	 * This is the thread on which all the IOIO activity happens. It will be run
	 * every time the application is resumed and aborted when it is paused. The
	 * method setup() will be called right after a connection with the IOIO has
	 * been established (which might happen several times!). Then, loop() will
	 * be called repetitively until the IOIO gets disconnected.
	 */
	class Looper extends BaseIOIOLooper
	{
		/**
		 * Called every time a connection with IOIO has been established.
		 * Typically used to open pins.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#setup()
		 */
		@Override
		protected void setup() throws ConnectionLostException
		{
			log("in setup");
			led_ = ioio_.openDigitalOutput(0, true);
			reset = ioio_.openDigitalOutput(MOTOR_RESET);// resets the motor controller chip for both motors
			reset.write(false);
			reset.write(true);

			motorControllerControl = ioio_.openDigitalOutput(MOTOR_CONTROLLER_CONTROL_PIN);
			motorControllerControl.write(true);// Slow decay

			halfFull = ioio_.openDigitalOutput(MOTOR_HALF_FULL_STEP_PIN);// both motors
			halfFull.write(true);// True = half step

			//			rightMotorDirection = ioio_.openDigitalOutput(MOTOR_RIGHT_DIRECTION_OUTPUT_PIN);
			//			rightMotorDirection.write(true);

			//			leftMotorDirection = ioio_.openDigitalOutput(MOTOR_LEFT_DIRECTION_OUTPUT_PIN);
			//			leftMotorDirection.write(false);

			motorEnable = ioio_.openDigitalOutput(MOTOR_ENABLE_PIN);// both motors
			motorEnable.write(true);

			//			rightMotorClockPulse = ioio_.openDigitalOutput(MOTOR_CLOCK_RIGHT_PIN);
			log("motor ready");

			try
			{
				sequencer_ = ioio_.openSequencer(config);
				sequencer_.waitEventType(Sequencer.Event.Type.STOPPED);
				while (sequencer_.available() > 0)
				{
					push();
				}
				sequencer_.start();

			} catch (Exception e)
			{
				e.printStackTrace();
			}

			log("sequencer started");
			/*
			 * When the setup() method is called the IOIO is connected.
			 */
			// ioiolog(getString(R.string.ioio_connected));

			/*
			 * Establish communication between the android and the VicsWagon via
			 * the ioio board
			 */
			// log(getString(R.string.wait_create));
			// log(getString(R.string.create_connected));

			// rearStrobe = ioio.openDigitalOutput(REAR_STROBE_ULTRASONIC_OUTPUT_PIN, false);
			// rear = ioio.openPulseInput(new DigitalInput.Spec(REAR_ULTRASONIC_INPUT_PIN),
			// PulseInput.ClockRate.RATE_62KHz,PulseInput.PulseMode.POSITIVE, false);
			// frontStrobe = ioio.openDigitalOutput(FRONT_STROBE_ULTRASONIC_OUTPUT_PIN, false);
			// front = ioio.openPulseInput(new DigitalInput.Spec(FRONT_ULTRASONIC_INPUT_PIN),
			// PulseInput.ClockRate.RATE_62KHz,PulseInput.PulseMode.POSITIVE,false);
			// leftStrobe = ioio.openDigitalOutput(LEFT_STROBE_ULTRASONIC_OUTPUT_PIN, false);
			// left = ioio.openPulseInput(new DigitalInput.Spec(LEFT_ULTRASONIC_INPUT_PIN),
			// PulseInput.ClockRate.RATE_62KHz, PulseInput.PulseMode.POSITIVE,true);
			// rightStrobe = ioio.openDigitalOutput(RIGHT_STROBE_ULTRASONIC_OUTPUT_PIN, false);
			// right = ioio.openPulseInput(new DigitalInput.Spec(RIGHT_ULTRASONIC_INPUT_PIN),
			// PulseInput.ClockRate.RATE_62KHz, PulseInput.PulseMode.POSITIVE, true);
			// rightMotorClock = ioio_.openPwmOutput(MOTOR_CLOCK_RIGHT_PIN, rightMotorPWMfrequency);//pin #, frequency right motor
			// leftMotorClock = ioio_.openPwmOutput(MOTOR_CLOCK_LEFT_PIN, leftMotorPWMfrequency);//pin #, frequency left motor
			// rightMotorClock.setPulseWidth(pulseWidth);
			// leftMotorClock.setPulseWidth(pulseWidth);
			// MediaPlayer mp_file = MediaPlayer.create(this, R.raw.your_song_file);
			// mp_file.start();
		}

		/**
		 * Called repetitively while the IOIO is connected.
		 * 
		 * @throws ConnectionLostException
		 *             When IOIO connection is lost.
		 * 
		 * @see ioio.lib.util.AbstractIOIOActivity.IOIOThread#loop()
		 */
		@Override
		public void loop() throws ConnectionLostException
		{
			log("loop");
			led_.write(!button_.isChecked());
			push();
			//			rightMotorClockPulse.write(true);
			//			rightMotorClockPulse.write(false);
			//			SystemClock.sleep(100);
		}

		private void push()
		{
			stepperStepCue_.clk = Clock.CLK_2M;
			stepperStepCue_.pulseWidth = 10;
			stepperStepCue_.period = 10000;
			try
			{
				sequencer_.push(cue_, 62500);
			} catch (Exception e)
			{
			} 
		}
	}

	/**
	 * A method to create our IOIO thread.
	 * 
	 * @see ioio.lib.util.AbstractIOIOActivity#createIOIOThread()
	 */
	@Override
	protected IOIOLooper createIOIOLooper()
	{
		return new Looper();
	}

	/**
	 * Writes a message to the Dashboard instance.
	 * 
	 * @param msg
	 *            the message to write
	 */
	public void log(final String msg)
	{
		runOnUiThread(new Runnable()
		{
			public void run()
			{
				log.append(msg);
				log.append("\n");
				scroller.smoothScrollTo(0, log.getBottom());
			}
		});
	}

}
