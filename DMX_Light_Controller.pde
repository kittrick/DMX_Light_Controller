import dmxP512.*;
import processing.serial.*;

// --------------------------------------------------
// DMXP512 Variables
// --------------------------------------------------

DmxP512 dmxOutput;
int universeSize = 128;
String DMXPRO_PORT = "/dev/cu.usbserial-EN210483"; // This is the port that is working on OSX // Had to install the drivers mentioned here www.ftdichip.com/Drivers/VCP.htm // case matters ! on windows port must be upper cased.
// String DMX_PORT = "/dev/serial/by-id/usb-ENTTEC_DMX_USB_Pro_EN210483-if00-port0" // This is the port that seems to work on the Raspberry Pi // Part of this is the serial number of the device so it will have to be edited by anyone using a different device
int DMXPRO_BAUDRATE = 115000;

import controlP5.*;
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

void setup() {
  // Document Setup
  fullScreen();
  //size(800,800);
  smooth();
  colorMode(RGB, 1);
  background(0);

  font = createFont("GT-Pressura-Mono-Regular.ttf", 13);
  textFont(font);
  
  // --------------------------------------------------
  // Now using ControlP5 for UI
  // --------------------------------------------------
  
  cp5 = new ControlP5(this);
  Group g1 = cp5.addGroup("Control Panel")
    .setBackgroundColor(color(1, 0.25))
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

void draw() {
  // Black Background
  background(0);
  fillStroke(0, -1);

  // Draw Light Triangles
  for (int i = 0; i < triCount; i++) {
    pushMatrix();
      int pushX = int(width/(triCount+2)+width/(triCount+2)*(i+1)-260/(triCount+1));
      translate(pushX, height/2);
      scale(0.8, 0.8);
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
      fill(fill*0.25, fill*0.75, fill);
      rect(i*60, 10-map(channels[i].cBrightness,0,1,0,20)/2, 20, map(channels[i].cBrightness,0,1,0,20));
    popMatrix();
    drawWave(i);
  }
}

// function colorA will receive changes from 
// controller with name reset
void reset() {
  cp5.getController("triCount").setValue(7);
  cp5.getController("channelOffset").setValue(10);
  cp5.getController("speed").setValue(50);
  cp5.getController("brightness").setArrayValue(new float[]{0,100});
  cp5.getController("waveform").setValue(0);
  for(int i = 0; i < channels.length; i++){
    channels[i].theta = 0.0;
  }
}

// function colorA will receive changes from 
// controller with name quit
void quit() {
  // This triggers automatically on startup so we need to
  // test against frameCount
  if(frameCount > 0) exit();
}

void controlEvent(ControlEvent theControlEvent) {
  if(theControlEvent.isFrom("brightness")) {
    // min and max values are stored in an array.
    // access this array with controller().arrayValue().
    // min is at index 0, max is at index 1.
    minBrightness = int(theControlEvent.getController().getArrayValue(0));
    maxBrightness = int(theControlEvent.getController().getArrayValue(1));
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
  float speed = 0.01;
  int index;
  int parent;
  int channel;

  // Initialization Function
  LightBulb(int a, int b) {
    index = a;
    parent = b;
    channel = int(random(0, channelCount));
  }
  public LightBulb update() {
    channels[channel].update();
    fill = channels[channel].cBrightness;
    return this;
  }
  public LightBulb display() {
    noStroke();
    fill(fill*0.25, fill*0.75, fill);
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
  public float theta = 0.0;

  Channel(int i) {
    index = i;
    history = new float[width];
  }
  public Channel update() {
    updateHistory();
    dmxOutput.set(index+1, int(map(history[0],0,1,0,255)));
    return this;
  }
  public Channel updateHistory() {
    float amplitude = 1.0;
    for(int b = 0 ; b < history.length; b++){
      float c = noise(((millis()+1000+b)/60+index*channelOffset)*speed*0.001);
      float noiseWave = map(c, 0, 1, minBrightness * 0.01, maxBrightness * 0.01);
      
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

void drawWave(int channel){
  stroke(channel/6.0);
  noFill();
  for(int x = 0; x < channels[channel].history.length-1; x++){
    float y = channels[channel].history[x]*100;
    float y2 = channels[channel].history[x+1]*100;
    line(x,height-100+y,x+1,height-100+y2);
  }
  noStroke();
}

void fillStroke(float fill, float stroke) {
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