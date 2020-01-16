load("fx:base.js");
load("fx:controls.js");
load("fx:graphics.js");

var material = new PhongMaterial();
material.diffuseColor = Color.LIGHTGREEN;
material.specularColor = Color.rgb(30, 30, 30);

var meshView = Java.to([
    new Box(200, 200, 200),
    new Sphere(100),
    new Cylinder(100, 200)
], "javafx.scene.shape.Shape3D[]");

for (var i = 0; i != 3; i++) {
    meshView[i].material = material;
    meshView[i].translateX = (i + 1) * 220;
    meshView[i].translateY = 200;
    meshView[i].translateZ = 20;
    meshView[i].drawMode = DrawMode.FILL;
    meshView[i].cullFace = CullFace.BACK;
};

var pointLight = new PointLight(Color.WHITE);
pointLight.translateX = 800;
pointLight.translateY = -200;
pointLight.translateZ = -1000;

var root = new Group(meshView);
root.children.add(pointLight);

var scene = new Scene(root, 800, 400, true);
scene.fill = Color.rgb(127, 127, 127);
scene.camera = new PerspectiveCamera(false);
$STAGE.scene = scene;
$STAGE.show();
