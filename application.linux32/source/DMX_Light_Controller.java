import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import dmxP512.*; 
import processing.serial.*; 
import controlP5.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class DMX_Light_Controller extends PApplet {




// --------------------------------------------------
// DMXP512 Variables
// --------------------------------------------------

DmxP512 dmxOutput;
int universeSize = 128;
String DMXPRO_PORT = "/dev/cu.usbserial-EN210483"; //case matters ! on windows port must be upper cased.
int DMXPRO_BAUDRATE = 115000;


ControlP5 cp5;
Accordion accordion;

LightTriangle[] tris;
Channel[] channels;

int triCount = 7;
int channelCount = 4;
int channelOffset = 10;
int globalIndex = 0;
int speed = 50;
int minBrightness = 0;
int maxBrightness = 100;
int waveform = 0;

PFont font;

// --------------------------------------------------
// Setup and Draw Functions
// --------------------------------------------------

public void setup() {
  // Document Setup
  
  //size(800,800);
  
  colorMode(RGB, 1);
  background(0);

  font = createFont("GT-Pressura-Mono-Regular.ttf", 13);
  textFont(font);
  
  // --------------------------------------------------
  // Now using ControlP5 for UI
  // --------------------------------------------------
  
  cp5 = new ControlP5(this);
  Group g1 = cp5.addGroup("Control Panel")
    .setBackgroundColor(color(1, 0.25f))
    .setBackgroundHeight(250)
    ;
  cp5.addSlider("triCount")
   .setPosition(10,10)
   .setSize(200,20)
   .setRange(1,7)
   .setValue(7)
   .moveTo(g1)
   ; 
  cp5.addSlider("speed")
   .setPosition(10,40)
   .setSize(200,20)
   .setRange(0,100)
   .setValue(50)
   .moveTo(g1)
   ;
  cp5.addSlider("channelOffset")
   .setPosition(10,70)
   .setSize(200,20)
   .setRange(0,100)
   .setValue(10)
   .moveTo(g1)
   ;
  cp5.addRange("brightness")
   // disable broadcasting since setRange and setRangeValues will trigger an event
   .setBroadcast(false) 
   .setPosition(10,100)
   .setSize(200,40)
   .setHandleSize(20)
   .setRange(0,100)
   .setRangeValues(0,100)
   // after the initialization we turn broadcast back on again
   .setBroadcast(true)
   .moveTo(g1)
   ;
  cp5.addSlider("waveform")
   .setPosition(10,150)
   .setSize(200,20)
   .moveTo(g1)
   ;
  cp5.addButton("reset")
   .setValue(0)
   .setPosition(10,190)
   .setSize(200,20)
   .moveTo(g1)
   ;
  cp5.addButton("quit")
   .setValue(0)
   .setPosition(10,220)
   .setSize(200,20)
   .moveTo(g1)
   ;
  accordion = cp5.addAccordion("acc")
   .setPosition(30,30)
   .setSize(100,20)
   .setWidth(290)
   .addItem(g1);

  // Initialize Classes
  tris = new LightTriangle[triCount];
  for (int i = 0; i < triCount; i++) {
    tris[i] = new LightTriangle(i);
  }
  channels = new Channel[channelCount];
  for (int i =0; i < channelCount; i++) {
    channels[i] = new Channel(i);
  }
  
  printArray(Serial.list());
  dmxOutput = new DmxP512(this, universeSize, false);
  try {
    dmxOutput.setupDmxPro(DMXPRO_PORT, DMXPRO_BAUDRATE);
  } catch(Exception e){
    println(e);
    //exit();
  }
}

public void draw() {
  // Black Background
  background(0);
  fillStroke(0, -1);

  // Draw Light Triangles
  for (int i = 0; i < triCount; i++) {
    pushMatrix();
      int pushX = PApplet.parseInt(width/(triCount+2)+width/(triCount+2)*(i+1)-260/(triCount+1));
      translate(pushX, height/2);
      scale(0.8f, 0.8f);
      rotate(radians(180*i));
      tris[i].display();
    popMatrix();
  }

  for (int i = 0; i < channelCount; i++ ) {
    pushMatrix();
      translate(width-260, 30);
      fill(1);
      text("CH: "+i, i*60-10, 40);
      float fill = channels[i].cBrightness;
      rect(i*60, 0, 20, 20);
      fill(fill*0.25f, fill*0.75f, fill);
      rect(i*60, 10-map(channels[i].cBrightness,0,1,0,20)/2, 20, map(channels[i].cBrightness,0,1,0,20));
    popMatrix();
    drawWave(i);
  }
}

// function colorA will receive changes from 
// controller with name reset
public void reset() {
  cp5.getController("triCount").setValue(7);
  cp5.getController("channelOffset").setValue(10);
  cp5.getController("speed").setValue(50);
  cp5.getController("brightness").setArrayValue(new float[]{0,100});
  cp5.getController("waveform").setValue(0);
  for(int i = 0; i < channels.length; i++){
    channels[i].theta = 0.0f;
  }
}

// function colorA will receive changes from 
// controller with name quit
public void quit() {
  // This triggers automatically on startup so we need to
  // test against frameCount
  if(frameCount > 0) exit();
}

public void controlEvent(ControlEvent theControlEvent) {
  if(theControlEvent.isFrom("brightness")) {
    // min and max values are stored in an array.
    // access this array with controller().arrayValue().
    // min is at index 0, max is at index 1.
    minBrightness = PApplet.parseInt(theControlEvent.getController().getArrayValue(0));
    maxBrightness = PApplet.parseInt(theControlEvent.getController().getArrayValue(1));
  }
}

// --------------------------------------------------
// Light Triangle Class
// --------------------------------------------------

class LightTriangle {
  // Class Vars
  int lightCount = 10;
  int gap = 30;
  int index;
  LightBulb[] lights;

  // Initialization Function
  LightTriangle(int a) {
    index = a;
    lights = new LightBulb[lightCount];
    for (int i = 0; i < lightCount; i++) {
      lights[i] = new LightBulb(i, index);
    }
  }
  public LightTriangle display() {
    int[] x = new int[]{0,-1, 1,-2, 0, 2,-3,-1, 1, 3};
    int[] y = new int[]{0, 2, 2, 4, 4, 4, 6, 6, 6, 6};
    for(int i = 0; i < lightCount; i ++) {
      pushMatrix();
        translate(x[i]*gap, y[i]*gap);
        lights[i].update().display();
      popMatrix();
    }
    return this;
  }
}

// --------------------------------------------------
// Lightbulb Class
// --------------------------------------------------

class LightBulb {
  // Class Vars
  int d = 20;
  float fill;
  float speed = 0.01f;
  int index;
  int parent;
  int channel;

  // Initialization Function
  LightBulb(int a, int b) {
    index = a;
    parent = b;
    channel = PApplet.parseInt(random(0, channelCount));
  }
  public LightBulb update() {
    channels[channel].update();
    fill = channels[channel].cBrightness;
    return this;
  }
  public LightBulb display() {
    noStroke();
    fill(fill*0.25f, fill*0.75f, fill);
    ellipseMode(CENTER);
    ellipse(0, 0, d, d);
    fill(1);
    if ( (parent+1) % 2 == 0) {
      rotate(radians(180));
    }
    text("CH: "+channel, -15, 30);
    return this;
  }
}

// --------------------------------------------------
// Channel Class
// --------------------------------------------------

class Channel {
  public int index;
  public float cBrightness;
  public float[] history;
  public float theta = 0.0f;

  Channel(int i) {
    index = i;
    history = new float[width];
  }
  public Channel update() {
    updateHistory();
    dmxOutput.set(index+1, PApplet.parseInt(map(history[0],0,1,0,255)));
    return this;
  }
  public Channel updateHistory() {
    float amplitude = 1.0f;
    for(int b = 0 ; b < history.length; b++){
      float c = noise(((millis()+1000+b)/60+index*channelOffset)*speed*0.001f);
      float noiseWave = map(c, 0, 1, minBrightness * 0.01f, maxBrightness * 0.01f);
      
      long period = 50000000 / (speed+1);
      theta += (TWO_PI / period);
      float sinWave = sin(theta) * amplitude;
      
      history[b] = lerp(noiseWave, map(sinWave,-1,1,0,1), map(waveform,0,100,0,1));
    }
    cBrightness = history[0];
    return this;
  }
}

// --------------------------------------------------
// Helper Functions
// --------------------------------------------------

public void drawWave(int channel){
  stroke(channel/6.0f);
  noFill();
  for(int x = 0; x < channels[channel].history.length-1; x++){
    float y = channels[channel].history[x]*100;
    float y2 = channels[channel].history[x+1]*100;
    line(x,height-100+y,x+1,height-100+y2);
  }
  noStroke();
}

public void fillStroke(float fill, float stroke) {
  if (fill != -1) {
    fill(fill);
  } else {
    noFill();
  }
  if (stroke != -1) {
    stroke(stroke);
  } else {
    noStroke();
  }
}
  public void settings() {  fullScreen();  smooth(); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "DMX_Light_Controller" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
