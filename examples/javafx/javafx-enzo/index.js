/*
 * Start the script on the command line with: jjs -cp Enzo-0.2.4.jar -fx Main.js
 */
load("fx:base.js");
load("fx:controls.js");
load("fx:graphics.js");

var Random        = java.util.Random;
var lastTimerCall = 0;

var Gauge         = Packages.eu.hansolo.enzo.gauge.Gauge;
var Section       = Packages.eu.hansolo.enzo.common.Section;

var timer         = new AnimationTimer() {
    handle: function handle(now) {
        if (now > lastTimerCall + 5000000000) {
            gauge.setValue(new Random().nextDouble() * 40);
            lastTimerCall = now;
        }
    }
};


$STAGE.title = "Nashorn FX";

var gauge = new Gauge();
gauge.setTitle('NashornFX');
gauge.setUnit('°C');
gauge.setMinValue(0);
gauge.setMaxValue(40);
gauge.setSections(new Section(18, 26),
                  new Section(26, 32),
                  new Section(32, 40));
gauge.setStyle("-section-fill-0: rgba(0, 200, 50, 0.5);" +
               "-section-fill-1: rgba(200, 100, 0, 0.5);" +
               "-section-fill-2: rgba(200, 0, 0, 0.5);");

var root = new StackPane();
root.children.add(gauge);

$STAGE.scene = new Scene(root, 400, 400);
$STAGE.show();

timer.start();
