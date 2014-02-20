if (typeof(voteroom) == "undefined") voteroom = {};

$(function() {
	var MSG = {
		WEBSOCKET_NOT_SUPPORTED: "ブラウザがWebSocketをサポートしていません。",
		TOO_SHORT_NAME: "名前は4文字以上指定してください",
		DUPLICATE_NAME: "この名前は既に使用されています",
		VALID_NAME: "この名前を使用できます",
		CONFIRM_PATTERN: "確認のためもう一度パターンを入力してください",
		INPUT_PATTERN: "ルーム設定を変更するためのパターンロックを設定してください",
		RETRY_PATTERN: "パターンが異なります。もう一度パターンを設定しなおしてください"
	};
	voteroom.MakeRoomTop = function(uri) {
		function ok(msg) {
			$ok.text(msg);
			$ok.show();
			$ng.hide();
			$btnNext.removeAttr("disabled");
		}
		function ng(msg) {
			$ng.text(msg);
			$ok.hide();
			$ng.show();
			$btnNext.attr("disabled", "disabled");
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
			$patternMsg = $("#patternMsg"),
			$btnNext = $("#btnNext"),
			$btnPrev = $("#btnPrev"),
			pattern = null,
			socket;
		if (!window.WebSocket) {
			$("#onError span").text(MSG.WEBSOCKET_NOT_SUPPORTED);
			$("#onError").show();
		} else {
			socket = new WebSocket(uri);
			socket.onmessage = receiveEvent;
		}

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
				value = value.join("")
				if (pattern) {
					if (pattern == value) {
						alert("OK: " + value);
					} else {
						$patternMsg.text(MSG.RETRY_PATTERN);
						pattern = null;
					}
				} else {
					pattern = value;
					$patternMsg.text(MSG.CONFIRM_PATTERN);
				}
			}
		})
		$btnNext.click(function() {
			$("#step1").hide();
			$("#step2").fadeIn("slow");
		});
		$btnPrev.click(function() {
			pattern = null;
			$("#step2").hide();
			$("#step1").fadeIn("slow");
			$patternMsg.text(MSG.INPUT_PATTERN);
		})
		$("body").bind("touchmove", function(event) {
			event.preventDefault();
		});
	}
})
