if (typeof(voteroom) == "undefined") voteroom = {};

$(function() {
	var MSG = {
		WEBSOCKET_NOT_SUPPORTED: "ブラウザがWebSocketをサポートしていません。",
		TOO_SHORT_NAME: "名前は4文字以上指定してください",
		DUPLICATE_NAME: "この名前は既に使用されています",
		VALID_NAME: "この名前を使用できます"
	};
	voteroom.MakeRoomTop = function(uri) {
		function ok(msg) {
			$ok.text(msg);
			$ok.show();
			$ng.hide();
		}
		function ng(msg) {
			$ng.text(msg);
			$ok.hide();
			$ng.show();
		}
		function receiveEvent(event) {
			if (event.data == "OK") {
				ok(MSG.VALID_NAME);
			} else {
				ng(MSG.DUPLICATE_NAME);
			}
		}
		var $ok = $("#name-ok"),
			$ng = $("#name-ng"),
			patternSet = false,
			socket;
		if (!window.WebSocket) {
			$("#onError span").text(MSG.WEBSOCKET_NOT_SUPPORTED);
			$("#onError").show();
			return;
		}
		socket = new WebSocket(uri);
		socket.onmessage = receiveEvent;

		$("#roomName").keyup(function() {
			var text = $(this).val();
			if (text.length < 4) {
				ng(MSG.TOO_SHORT_NAME);
			} else if (socket) {
				socket.send(text);
			}
		}).focus();
		$("#pattern").patternInput({
			"onFinish" : function(value) {
				alert("Finish: " + value.join(","));
			}
		})
	}
})
