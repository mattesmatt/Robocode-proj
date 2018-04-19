package DodgeBot;
import robocode.*;
import java.awt.Color;
import robocode.util.Utils;


/**
 * DodgeBot - a robot by Matt
 */
 
public class DodgeBot extends AdvancedRobot
{
  private FireControlSystem fcs = null;
  public DodgeBot() {
      fcs = new FireControlSystem();
  }
  
  private String targetName = "";
  private double targetEnergy = 0;					
  private boolean moveForward = true;
  private boolean turnRadarRight = true;
  private boolean hitByBullet = false;
  private boolean changedDirections = false;
  private boolean closeRange = false;
  private int closeRangeCounter = 0;
  private int closeRangeMax = 200;					
  private int maxCloseCount = 20;
  private int maxWallDistance = 50;
  private int minMoveDistance = 30;
  private int maxMoveDistance = 100;
  private int maxEnemyDistance = 300;
  private int fireDistance = 200;
  private int fireAngle = 45;
  private double firePower = 3;
  
  
  @Override
  public void run() {
    
    setBodyColor(Color.red);
    setGunColor(Color.black);
    setRadarColor(Color.green);
    setAdjustGunForRobotTurn(true);
    setAdjustRadarForGunTurn(true);
    setAdjustRadarForRobotTurn(true);
    // Circles the radar infinitely until scans target
   
    turnRadarRightRadians(Double.POSITIVE_INFINITY);
    // Chooses normal and closeRange
    while (true) {
      
      if (this.closeRange) {
        this.meleeBehavior();
      }
      else {
        this.normalBehavior();
      }
    }
  }
    public void executeCommand(FireCommand cmd){
        double fireAngle = cmd.getDegreesToRotateGun();
        //rotate first, anyway
        if(fireAngle<=180)
			this.turnGunRight(fireAngle);
		else
			this.turnGunLeft(360-fireAngle);
        //whether to fire bullet depends on "toFire" in command
		if(cmd.getToFire()){
  			fire(cmd.getBulletPower());
        }
    }
  @Override
  public void onScannedRobot(ScannedRobotEvent e) {
        BattleData data = this.collectBattleData(e);
        FireCommand fc = fcs.calcFireAngle(data);
        this.executeCommand(fc);
    // moves the direction of the radar
    double radarTurn = getHeadingRadians() + e.getBearingRadians() - getRadarHeadingRadians();
    setTurnRadarRightRadians(Utils.normalRelativeAngle(radarTurn));
    
    // Detected robot is target
    if (this.targetName.equals("")) {
      this.targetName = e.getName();
    }
    
    // If not in closeRange, dodge shots unless target gets close
    if (this.targetName.equals(e.getName())) {
      
      // If in closeRange mode, Attack!
      if (this.closeRange) {
        this.closeAttack(e);
        return;
      }
      
      //Finishes the target if they are disabled
      if (e.getEnergy() <= 0) {
        this.finishHim(e);
        return;
      }
    
      // If enemy energy hasnt been set in battleData, does it here globally
      if (this.targetEnergy <= 0) {
        this.targetEnergy = e.getEnergy();
      }
      
      // If our robot gets too close and not in closeRange, Fire Bullet without using ControlSystem
      if (e.getDistance() <= this.fireDistance && Math.abs(e.getBearing()) <= fireAngle) {
        this.fire(this.firePower);
      }
      
      //Dodging Algorithm implemented
      this.dodgeBullets(e);
      
      //Better positioning to set up dodging by staying perpendicular to enemy
      this.turnRight(e.getBearing() + 90);
      
      //Adds to counter for closeRange and goes into it if reached
      this.checkCloseRange(e);
    }
  }
  
  @Override
  public void onHitByBullet(HitByBulletEvent event) {
    this.hitByBullet = true;
  }
  
  @Override
  public void onHitRobot(HitRobotEvent event) {
    //If hits another robot besides target(in multiple robot fights), shoots them if they are in the firing angle
    if (Math.abs(event.getBearing()) <= this.fireAngle) {
      this.fire(this.firePower);
    }
  }
  
  @Override
  public void onHitWall(HitWallEvent event) {
    this.changedDirections = true;
    
    if (this.moveForward) {
      this.moveForward = false;
    }
    else {
      this.moveForward = true;
    }
  }
  
  //Actions taken to avoid the wall when dodging
  public boolean preventWallHit() {
    
    double distanceFromLeft = this.getX();
    double distanceFromRight = this.getBattleFieldWidth() - this.getX();
    double distanceFromTop = this.getBattleFieldHeight() - this.getY();
    double distanceFromBottom = this.getY();
    
    // When Bot is facing upwards
    if (this.getHeading() >= 315 || this.getHeading() <= 45) {
      
      if (this.moveForward && distanceFromTop < this.maxWallDistance) {
        return true;
      }
      if (!this.moveForward && distanceFromBottom < this.maxWallDistance) {
         return true;
      }
      return false;
      
    }
    // When Bot is facing to the right
    else if (this.getHeading() >= 45 && this.getHeading() <= 135) {
      
      if (this.moveForward  && distanceFromRight < this.maxWallDistance) {
        return true;
      }
      if (!this.moveForward && distanceFromLeft < this.maxWallDistance) {
        return true;
      }
      return false;
      
    }
    // When bot is facing downwards
    else if (this.getHeading() >= 135 && this.getHeading() <= 225) {
      
      if (this.moveForward && distanceFromBottom < this.maxWallDistance) {
        return true;
      }
      if (!this.moveForward && distanceFromTop < this.maxWallDistance) {
        return true;
      }
      return false;
      
    }
    // When bot is facing to the left
    else {
      
      if (this.moveForward && distanceFromLeft < this.maxWallDistance) {
        return true;
      }
      if (!this.moveForward && distanceFromRight < this.maxWallDistance) {
        return true;
      }
      return false;
      
    }
  }
  
  //CloseRange Mode Attack!
  public void closeAttack(ScannedRobotEvent e) {
    this.turnRight(e.getBearing());
    if (e.getDistance() > this.fireDistance) {
      this.ahead(e.getDistance());
    }
    if (e.getDistance() <= this.fireDistance && Math.abs(e.getBearing()) <= this.fireAngle) {
      this.fire(this.firePower);
    }
  }
  
  //The dodging semantics
  public void dodgeBullets(ScannedRobotEvent e) {
    
    // If a shot is fired, if bot was hit, or if enemy is close, we dodge!
    if (e.getEnergy() != this.targetEnergy ||
        this.hitByBullet ||
        e.getDistance() < this.maxEnemyDistance) {
      
      // In case we are too close to wall
      if (this.preventWallHit()) {
        this.changedDirections = true;
        
        if (this.moveForward) {
          this.moveForward = false;
        }
        else {
          this.moveForward = true;
        }
      }
      
      
      //Dodge! 
      //Moves twice as much if just changed directions.In order to avoid moving back into the path of a bullet.
      int moveMin = this.minMoveDistance;
      double moveMax = this.maxMoveDistance * (e.getDistance() /
                       Math.hypot(this.getBattleFieldWidth(), this.getBattleFieldHeight()));
      double actualMove = this.maxMoveDistance - Math.max(moveMin, moveMax);
      
      if (this.changedDirections) {
        actualMove *= 2;
      }
      
      if (this.moveForward) {
        this.ahead(actualMove);
      }
      else {
        this.back(actualMove);
      }
      
      // Reset Values to redodge another bullet
      this.targetEnergy = e.getEnergy();
      this.hitByBullet = false;
      this.changedDirections = false;
    }
  }
  
 //Counter for closeRange, if reached: Enter CloseRange
  public void checkCloseRange(ScannedRobotEvent e) {
    if (e.getDistance() <= this.closeRangeMax) {
      this.closeRangeCounter++;
    }
    if (this.closeRangeCounter >= this.maxCloseCount) {
      this.closeRange = true;
    }
  }
  
  //When enemy is disabled, finishHim!
  public void finishHim(ScannedRobotEvent e) {
    this.turnRight(e.getBearing());
    this.fire(3);
    this.targetName = "";
    this.targetEnergy = 0;
  }
  
  //The two modes of acting, normal=dodging, melee=closeRange attacks
  public void normalBehavior() {
    if (this.turnRadarRight) {
      this.turnRadarRight(45);
    }
    else {
      this.turnRadarLeft(45);
    }
  }
  
  public void meleeBehavior() {
    if (this.getRadarHeading() != this.getHeading()) {
      this.turnRadarRight(this.getHeading() - this.getRadarHeading());
      this.setAdjustRadarForRobotTurn(false);
    }
    this.turnRight(359);
  }
  
  public boolean isMovingForward() {
    return this.moveForward;
  }
  
  public boolean isIncloseRange() {
    return this.closeRange;
  }
  
  //Returns the counter for closeRange mode when needed
  public int getcloseRangeCounter() {
    return this.closeRangeCounter;
  }
  

  public int closeRangeCounterMax() {
    return this.maxCloseCount;
  }
public BattleData collectBattleData(ScannedRobotEvent e){
        BattleData data = new BattleData(
                this.getX(),
                this.getY(),
                this.getHeading(),
                this.getGunHeading(),
                e.getBearing(),
                e.getVelocity(),
                e.getHeading(),
                e.getDistance());
        return data;
    } 
}