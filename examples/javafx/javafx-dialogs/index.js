#!/usr/bin/jjs -fx
/*
 * Como executar: jjs -fx post24DialogosEntradas.js
 */
load("fx:base.js");
load("fx:graphics.js");
load("fx:controls.js");

var nome, regiao, fazExe;

lblTitulo = new Label("Questionário Importante");
btnNome = new Button("Entrar com Nome");
btnRegiao = new Button("Região que mora");
btnExe = new Button("Faz exercício?");
btnRes = new Button("Ver Resultado");
 
lblTitulo.setFont(javafx.scene.text.Font.font(24)); 

btnNome.setOnAction(function(e) {
	dialogoNome = new TextInputDialog();
	dialogoNome.setTitle("Entrada de nome");
	dialogoNome.setHeaderText("Entre com seu nome");
	dialogoNome.setContentText("Nome:");
	// se o usuário fornecer um valor, assignamos ao nome
	dialogoNome.showAndWait().ifPresent(function(v) { nome = v});
});

btnRegiao.setOnAction(function(e) {
	// o primeiro parâmetro é a escola padrão e os outros são os valores da Choice Box
	dialogoRegiao = new ChoiceDialog("Sul", "Sul", "Leste", "Oeste", "Norte");
	dialogoRegiao.setTitle("Entrada de Região");
	dialogoRegiao.setHeaderText("Informe sua região abaixo");
	dialogoRegiao.setContentText("Região:");
	dialogoRegiao.showAndWait().ifPresent(function(r) { regiao = r.toString() });
});

btnExe.setOnAction(function(e) {
	dialogoExe = new Alert(Alert.AlertType.CONFIRMATION);
	btnSim = new ButtonType("Sim");
	btnNao = new ButtonType("Não");
	btnAsVezes = new ButtonType("As vezes");
	btnNaoResponder = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
 
	dialogoExe.setTitle("Pergunta sobre exercício");
	dialogoExe.setHeaderText("Informe se você faz exercício");
	dialogoExe.setContentText("Você faz exercício?");
	dialogoExe.getButtonTypes().setAll(btnSim, btnNao, btnAsVezes, btnNaoResponder);
	dialogoExe.showAndWait().ifPresent(function(b) {
		if (b === btnSim) {
			fazExe = "faz exercício";
		} else if (b === btnNao) {
			fazExe = "não faz exercício";
		} else if (b === btnAsVezes) {
			fazExe = "faz exercício as vezes";
		} else {
			fazExe = "não quis responder";
		}
	});
});
 
btnRes.setOnAction(function(e) {
	dialogoResultado = new Alert(Alert.AlertType.INFORMATION);
	dialogoResultado.setHeaderText("Resultado do questionário");
	dialogoResultado.setContentText(nome + " mora na região " + regiao + " e " + fazExe + ".");
	dialogoResultado.showAndWait(); 
});
 
raiz = new VBox(20);
raiz.setAlignment(Pos.CENTER);
raiz.getChildren().addAll(lblTitulo, btnNome, btnRegiao, btnExe, btnRes);
 
$STAGE.title = "Diálogos para entradas dos usuários";
$STAGE.scene = new Scene(raiz, 450, 250);
$STAGE.show(); 
