package org.firstinspires.ftc.teamcode.auton;

import static org.firstinspires.ftc.teamcode.drive.DriveConstants.variable_slide_ticks;
import static org.firstinspires.ftc.teamcode.drive.DriveConstants.variable_tilt_ticks;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.outoftheboxrobotics.photoncore.PhotonCore;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.trajectorysequence.TrajectorySequence;
import org.openftc.apriltag.AprilTagDetection;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;

import java.util.ArrayList;


// adb connect 192.168.43.1:5555

@Autonomous(name="AUTOExtendingMeet2")
public class AUTOExtendingMeet2 extends OpMode {

    public void init_loop(){
        {
            ArrayList<AprilTagDetection> currentDetections = aprilTagDetectionPipeline.getLatestDetections();

            if(currentDetections.size() != 0)
            {
                boolean tagFound = false;

                for(AprilTagDetection tag : currentDetections)
                {
                    if(tag.id == LEFT || tag.id == MIDDLE || tag.id == RIGHT)
                    {
                        tagOfInterest = tag;
                        // Here we set the integer we KNOW is is one of the three from the test above
                        parkingTag = tag.id;
                        tagFound = true;
                        break;
                    }
                }

                if(tagFound)
                {
                    telemetry.addLine("Tag of interest is in sight!\n\nLocation data:");
                    tagToTelemetry(tagOfInterest);
                }
                else
                {
                    telemetry.addLine("Don't see tag of interest :(");

                    if(tagOfInterest == null)
                    {
                        telemetry.addLine("(The tag has never been seen)");
                    }
                    else
                    {
                        telemetry.addLine("\nBut we HAVE seen the tag before; last seen at:");
                        tagToTelemetry(tagOfInterest);
                    }
                }

            }
            else
            {
                telemetry.addLine("Don't see tag of interest :(");

                if(tagOfInterest == null)
                {
                    telemetry.addLine("(The tag has never been seen)");
                }
                else
                {
                    telemetry.addLine("\nBut we HAVE seen the tag before; last seen at:");
                    tagToTelemetry(tagOfInterest);
                }

            }

            telemetry.update();
        }

        if(tagOfInterest != null)
        {
            telemetry.addLine("Tag snapshot:\n");
            tagToTelemetry(tagOfInterest);
            telemetry.update();
        }
        else
        {
            telemetry.addLine("No tag snapshot available, it was never sighted during the init loop :(");
            telemetry.update();
        }
    }

    public enum LiftState {
        LIFT_STARTDROP,
        LIFT_GETNEW,
        LIFT_RETRACTSLIDE,
        LIFT_HOLD,
        LIFT_LETGO,
        LIFT_DROPCYCLE,
        LIFT_INC,
        PARKING_STATE,
        LIFT_WAITSTATE,
        FINISH
    }


    // The liftState variable is declared out here
    // so its value persists between loop() calls
    LiftState liftState = LiftState.LIFT_STARTDROP;

    OpenCvCamera camera;
    AprilTagDetectionPipeline aprilTagDetectionPipeline;

    static final double FEET_PER_METER = 3.28084;

    double fx = 578.272;
    double fy = 578.272;
    double cx = 402.145;
    double cy = 221.506;

    // UNITS ARE METERS
    double tagsize = 0.166;

    // Tag ID 1,2,3 from the 36h11 family
    int LEFT = 1;
    int MIDDLE = 2;
    int RIGHT = 3;

    // This Integer will be set to a default LEFT if no tag is found
    int parkingTag = LEFT;

    AprilTagDetection tagOfInterest = null;

    TrajectorySequence BlueOnRedGoMiddle;
    TrajectorySequence BlueOnRedGoRight;
    TrajectorySequence BlueOnRedGoLeft;


    public DcMotorEx slide_extension;
    public DcMotorEx tilt_arm;
    public DcMotorEx rotate_arm;
    public Servo claw;
    public Servo tilt_claw;
    public Servo odometry_forward;
    public Servo odometry_strafe;
    //public VoltageSensor voltageSensor;

    ElapsedTime liftTimer = new ElapsedTime();
    ElapsedTime parkingTimer = new ElapsedTime();

    SampleMecanumDrive drive;

    int cones_dropped = 0;
    int CONES_DESIRED = 4;


    final double CLAW_HOLD = 0.45; // the idle position for the dump servo
    final double CLAW_DEPOSIT = 0.7; // the dumping position for the dump servo

    final double CLAWTILT_END = 0.27;
    final double CLAWTILT_COLLECT = 0.50;
    final double CLAWTILT_DEPOSIT = .70;

    // the amount of time the dump servo takes to activate in seconds
    final double DUMP_TIME = 1;
    final double ROTATE_TIME = 0.3; // the amount of time it takes to rotate 135 degrees
    final double EXTENSION_TIME = 0.6; // e amount of time it takes to extend from 0 to 2250 on the slide

    final int SLIDE_LOW = 200; // the low encoder position for the lift
    final int SLIDE_COLLECT = 1340; // the high encoder position for the lift
    final int SLIDE_DROPOFF = 1360;
    final int SLIDE_MOVEMENT = 1125; // the slide retraction for when rotating

    // TODO: find encoder values for tilt
    int TILT_LOW = 130;
    final int TILT_HIGH = 450;
    public int TILT_DECREMENT = 435;

    // TODO: find encoder values for rotation
    final int ROTATE_COLLECT = -2235;
    final int ROTATE_DROP = -1215;

    //public TrajectorySequence VariablePath;

    public void init() {
        liftTimer.reset();
        //PhotonCore.enable();


        drive = new SampleMecanumDrive(hardwareMap);

        drive.setPoseEstimate(new Pose2d(0, 0, Math.toRadians(270)));

        slide_extension = hardwareMap.get(DcMotorEx.class,"slide_extension");
        tilt_arm = hardwareMap.get(DcMotorEx.class,"tilt_arm");
        rotate_arm = hardwareMap.get(DcMotorEx.class,"rotate_arm");
        claw = hardwareMap.get(Servo.class,"claw");
        tilt_claw = hardwareMap.get(Servo.class,"tilt_claw");

        odometry_forward = hardwareMap.get(Servo.class, "odometry_forward");
        odometry_strafe = hardwareMap.get(Servo.class, "odometry_strafe");

        odometry_forward.setPosition(1);
        odometry_strafe.setPosition(0.25);

        //VoltageSensor voltageSensor = hardwareMap.voltageSensor.iterator().next();

        //rotate_arm = hardwareMap.get(DcMotorEx.class,"rotate_arm");

        slide_extension.setDirection(DcMotor.Direction.REVERSE);
        slide_extension.setTargetPosition(variable_slide_ticks);
        slide_extension.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        slide_extension.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        tilt_arm.setTargetPosition(variable_tilt_ticks);
        tilt_arm.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        tilt_arm.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        rotate_arm.setTargetPosition(0);
        rotate_arm.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        rotate_arm.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        claw.setPosition(CLAW_HOLD);
        tilt_claw.setPosition(0.15);



        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        camera = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "Webcam 1"), cameraMonitorViewId);
        aprilTagDetectionPipeline = new AprilTagDetectionPipeline(tagsize, fx, fy, cx, cy);

        camera.setPipeline(aprilTagDetectionPipeline);
        camera.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                camera.startStreaming(800,448, OpenCvCameraRotation.UPRIGHT);
            }

            @Override
            public void onError(int errorCode)
            {

            }
        });


        telemetry.setMsTransmissionInterval(50);


    /*    init_loop();{
         }*/
        //while (tagOfInterest == null)


        BlueOnRedGoRight = drive.trajectorySequenceBuilder(new Pose2d(3.7,-52, Math.toRadians(270)))
                .strafeRight(26)
                .build();
        BlueOnRedGoLeft = drive.trajectorySequenceBuilder(new Pose2d(3.7,-52, Math.toRadians(270)))
                .strafeLeft(17)
                .build();

        TrajectorySequence BlueOnRedGoCycle = drive.trajectorySequenceBuilder(new Pose2d(0, 0, Math.toRadians(270)))
                //.lineTo(new Vector2d(0,-32))
                //.addDisplacementMarker(() -> switchvar = true)
                //.lineTo(new Vector2d(0,-48))
                .splineToConstantHeading(new Vector2d(0, -50), Math.toRadians(270))
                //.strafeLeft(3.7)
                .build();
        init_loop();
        drive.followTrajectorySequenceAsync(BlueOnRedGoCycle);

    }

    public void loop() {
        //Pose2d poseEstimate = drive.getPoseEstimate();

/*        if (drive.getPoseEstimate().getY() >= -47){
            drive.setPoseEstimate(new Pose2d(0, poseEstimate.getY(), poseEstimate.getHeading()));
        }*/

        telemetry.addData("x", (drive.getPoseEstimate()).getX());
        //telemetry.addData("x2", poseEstimate.getX());
        telemetry.addData("y", (drive.getPoseEstimate()).getY());
        //telemetry.addData("y2", poseEstimate.getY());
        //telemetry.addData("heading", poseEstimate.getHeading());
        telemetry.addData("encoder ticks for slide",slide_extension.getCurrentPosition());
        telemetry.addData("encoder ticks for tilt",tilt_arm.getCurrentPosition());
        telemetry.addData("rotation ticks", rotate_arm.getCurrentPosition());
        telemetry.addData("claw position", claw.getPosition());
        telemetry.addData("claw tilt", tilt_claw.getPosition());
        telemetry.addData("timer",liftTimer.seconds());
        telemetry.addData("liftstate", liftState);
        telemetry.addData("cones dropped", cones_dropped);
        //telemetry.addData("movedForward", movedForward);
        //telemetry.addData("tag location", tagOfInterest.id);
        telemetry.addData("drive", drive.isBusy());



        //telemetry.update();
        drive.update();
/*
        Pose2d noX = new Pose2d(0, poseEstimate.getY(), poseEstimate.getHeading());
*/

        //drive.updatePoseEstimate();

/*        if ((poseEstimate.getY() <= -47.75) && (poseEstimate.getY()) >= -48 && !(movedForward)) {
            drive.setPoseEstimate(new Pose2d(0, -48, Math.toRadians(270)));
            movedForward = true;
        }*/


/*        switch (liftState) {
            case LIFT_STARTDROP:
                drive.update();
                rotate_arm.setPower(0.5);
                tilt_arm.setPower(0.5);
                slide_extension.setPower(1);
                tilt_arm.setTargetPosition(TILT_HIGH-10);
                rotate_arm.setTargetPosition(ROTATE_DROP);
                tilt_claw.setPosition(CLAW_DEPOSIT);
                if (Math.abs(rotate_arm.getCurrentPosition() - ROTATE_DROP) <= 30) {
                    slide_extension.setTargetPosition(SLIDE_DROPOFF);
                    tilt_claw.setPosition(CLAWTILT_DEPOSIT);
                    rotate_arm.setPower(0.5);
                    if ((Math.abs(slide_extension.getCurrentPosition() - SLIDE_DROPOFF) <= 8) && (Math.abs(tilt_arm.getCurrentPosition() - TILT_HIGH) <= 17)) {
                        liftTimer.reset();
                        claw.setPosition(CLAW_DEPOSIT);
                        liftState = LiftState.LIFT_INC;
                    }
                }
                break;

// Q's DropCycle
*//*            case LIFT_DROPCYCLE:
                tilt_arm.setPower(1);
                if (tilt_arm.getCurrentPosition() < 180) {
                    tilt_arm.setTargetPosition(TILT_HIGH);
                    break;
                }
                rotate_arm.setTargetPosition(ROTATE_DROP);   *//*
            case LIFT_DROPCYCLE:
                tilt_arm.setPower(1);
                tilt_arm.setTargetPosition(TILT_HIGH);
                if (tilt_arm.getCurrentPosition() >= 180) {
                    rotate_arm.setTargetPosition(ROTATE_DROP);
                    if (Math.abs(rotate_arm.getCurrentPosition() - ROTATE_DROP) <= 10) {
                        slide_extension.setTargetPosition(SLIDE_DROPOFF);
                        tilt_claw.setPosition(CLAWTILT_DEPOSIT);
                        if ((Math.abs(slide_extension.getCurrentPosition() - SLIDE_DROPOFF) <= 8) && (Math.abs(tilt_arm.getCurrentPosition() - TILT_HIGH) <= 5) && (rotate_arm.getCurrentPosition() - ROTATE_DROP) <= 8) {
                            claw.setPosition(CLAW_DEPOSIT);
                            liftTimer.reset();
                            liftState = LiftState.LIFT_INC;
                        }
                    }
                }
                break;

            case LIFT_GETNEW:
                tilt_arm.setPower(0.5);
                tilt_arm.setTargetPosition(TILT_LOW);
                rotate_arm.setTargetPosition(ROTATE_COLLECT); // rotates to the stack of cones
                if (Math.abs(rotate_arm.getCurrentPosition() - ROTATE_COLLECT) <= 400){
                    // if the rotation is within 100 ticks begin to drop the tilt arm to the low position
                    tilt_claw.setPosition(CLAWTILT_COLLECT);
                    tilt_arm.setPower(0.3);
                    tilt_arm.setTargetPosition(TILT_LOW);
                    if (tilt_arm.getCurrentPosition() - TILT_LOW <= 5){
                        // once the tilt is close etend the slide
                        slide_extension.setTargetPosition(SLIDE_COLLECT);
                        tilt_arm.setPower(0.1);
                        if (slide_extension.getCurrentPosition() >= (SLIDE_COLLECT-8) && tilt_arm.getCurrentPosition() - TILT_LOW <= 1) {
                            // once they are both within a certain tick value grab with the claw
                            claw.setPosition(CLAW_HOLD);
                            rotate_arm.setPower(0.5);
                            liftTimer.reset();
                            liftState = LiftState.LIFT_HOLD;
                        }
                    }
                }
                break;

            case LIFT_HOLD:
                if (liftTimer.seconds() >= 0.4) {
                    liftState = LiftState.LIFT_DROPCYCLE;
                }
                break;

            case LIFT_INC:
                if (cones_dropped <= CONES_DESIRED) {
                    if (liftTimer.seconds() >= 0.5) {
                        cones_dropped += 1;
                        TILT_LOW = TILT_LOW-20;
                        liftTimer.reset();
                        liftState = LiftState.LIFT_RETRACTSLIDE;
                    }
                }
                else {
                    if (liftTimer.seconds() >= 0.5) {

                        liftTimer.reset();
                        liftState = LiftState.PARKING_STATE;
                    }
                }
                break;
            case LIFT_RETRACTSLIDE:
                rotate_arm.setPower(1);
                slide_extension.setTargetPosition(SLIDE_LOW);
                rotate_arm.setTargetPosition(ROTATE_COLLECT);
                tilt_claw.setPosition(0.3);
                if (slide_extension.getCurrentPosition() <= 450) {
                    liftTimer.reset();
                    //liftState = LiftState.LIFT_GETNEW;
                    liftState = LiftState.LIFT_GETNEW;
                }
                break;
            case PARKING_STATE:
                liftTimer.reset();
                // Use the parkingTag here - it must be at least LEFT if no tag was seen
                if (parkingTag == LEFT){ //&& cones_dropped >= CONES_DESIRED) {

                    drive.followTrajectorySequenceAsync(BlueOnRedGoLeft);
                    liftTimer.reset();
                    telemetry.addData("left", 1);
                    liftState = LiftState.FINISH;


                } else if (parkingTag == RIGHT){ //&& cones_dropped >= CONES_DESIRED) {

                    drive.followTrajectorySequenceAsync(BlueOnRedGoRight);
                    liftTimer.reset();
                    telemetry.addData("right", 2);
                    liftState = LiftState.FINISH;



                } else if (parkingTag == MIDDLE){ //&& cones_dropped >= CONES_DESIRED) {

                    liftTimer.reset();
                    telemetry.addData("middle", 3);
                    liftState = LiftState.FINISH;

                }
                liftState = LiftState.FINISH;
                break;
            case FINISH:
                drive.update();
                slide_extension.setTargetPosition(0);
                tilt_claw.setPosition(CLAWTILT_END);
                if (liftTimer.seconds() >= 0.5) {
                    rotate_arm.setPower(1);
                    rotate_arm.setTargetPosition(0);
                    tilt_arm.setTargetPosition(0);
                }
                break;



        }*/
    }

    void tagToTelemetry(AprilTagDetection detection)
    {
        telemetry.addLine(String.format("\nDetected tag ID=%d", detection.id));
        telemetry.addLine(String.format("Translation X: %.2f feet", detection.pose.x*FEET_PER_METER));
        telemetry.addLine(String.format("Translation Y: %.2f feet", detection.pose.y*FEET_PER_METER));
        telemetry.addLine(String.format("Translation Z: %.2f feet", detection.pose.z*FEET_PER_METER));
        telemetry.addLine(String.format("Rotation Yaw: %.2f degrees", Math.toDegrees(detection.pose.yaw)));
        telemetry.addLine(String.format("Rotation Pitch: %.2f degrees", Math.toDegrees(detection.pose.pitch)));
        telemetry.addLine(String.format("Rotation Roll: %.2f degrees", Math.toDegrees(detection.pose.roll)));
    }
}