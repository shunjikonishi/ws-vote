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
	function Control($el) {
		var $basic = $("#basic"),
			$buttons = $("#buttons"),
			$password = $("#password"),
			$title = $("#ctrl-title"),
			$leftIcon = $("#icon-left"),
			$rightIcon = $("#icon-right");

		function left() {
			var $hide, $show;
			if ($basic.is(":visible")) {
				$hide = $basic;
				$show = $buttons;
			} else if ($buttons.is(":visible")) {
				$hide = $buttons;
				$show = $password;
			}
			if ($hide && $show) {
				show($hide, $show, "left");
			}
		}
		function right() {
			if ($password.is(":visible")) {
				$hide = $password;
				$show = $buttons;
			} else if ($buttons.is(":visible")) {
				$hide = $buttons;
				$show = $basic;
			}
			if ($hide && $show) {
				show($hide, $show, "right");
			}
		}
		function show($hide, $show, direction) {
			$hide.hide();
			$show.show();
			$title.text($show.attr("data-title"));
			if ($show == $basic) {
				$leftIcon.hide();
				$rightIcon.show();
			} else if ($show == $password) {
				$leftIcon.show();
				$rightIcon.hide();
			} else {
				$leftIcon.show();
				$rightIcon.show();
			}
		}
		$el.swipe({
			swipe:function(event, direction, distance, duration, fingerCount) {
				switch (direction) {
					case "right":
						right();
						break;
					case "left":
						left();
						break;
				}
			},
			threshold: 0
		});
	}
	voteroom.EditRoom = function(setting) {
		$("#pattern").patternInput({
			"onFinish" : function(value) {
				value = value.join("")
				alert("OK: " + value);
			}
		});
		var ctrl = new Control($("#ctrl"));
	}
})
