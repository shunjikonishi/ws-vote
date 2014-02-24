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
	function isMobile() {
		return navigator.userAgent.indexOf('iPhone') > 0 ||
			navigator.userAgent.indexOf('iPad') > 0 ||
			navigator.userAgent.indexOf('iPod') > 0 ||
			navigator.userAgent.indexOf('Android') > 0 ||
			navigator.userAgent.indexOf('Mobile') > 0;
	}
	function rgb2hex(str) {
		var parts = str.match(/^rgb\((\d+),\s*(\d+),\s*(\d+)\)$/);
		if (parts.length == 4) {
			delete parts[0];
			for (var i = 1; i <= 3; ++i) {
			    parts[i] = parseInt(parts[i]).toString(16);
			    if (parts[i].length == 1) parts[i] = '0' + parts[i];
			} 
			return '#'+parts.join('');
		}
		return str;
	}
	function Control($el) {
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
				show($hide, $show, "right");
			}
		}
		function right() {
			var $hide, $show;
			if ($password.is(":visible")) {
				$hide = $password;
				$show = $buttons;
			} else if ($buttons.is(":visible")) {
				$hide = $buttons;
				$show = $basic;
			}
			if ($hide && $show) {
				show($hide, $show, "left");
			}
		}
		function show($hide, $show, direction) {
			$hide.hide();
			$show.show("slide", {"direction" : direction}, 750, function() {
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
			});
		}
		var $basic = $("#basic"),
			$buttons = $("#buttons"),
			$password = $("#password"),
			$title = $("#ctrl-title"),
			$leftIcon = $("#icon-left"),
			$rightIcon = $("#icon-right");

		$(".swipable").swipe({
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
		$("body").bind("touchmove", function(event) {
			if ($password.is(":visible")) {
				event.preventDefault();
			}
		});
		$leftIcon.click(right);
		$rightIcon.click(left);
	}
	voteroom.EditRoom = function(setting) {
		function init() {
			$("#voteLimit").datetimepicker({
				"useSeconds" : false,
				"minuteStepping" : 15
			});
			$("#viewLimit").datetimepicker({
				"pickTime" : false
			});
			$(".vote").each(function() {
				var $b = $(this);
				$b.attr("data-color", $b.css("background-color"));
			});
			var evtStart, evtEnd;
			if (isMobile()) {
				evtStart = "touchstart";
				evtEnd = "touchend";
			} else {
				evtStart = "mousedown";
				evtEnd = "mouseup";
			}
			$(".vote").bind(evtStart, function() {
				var $b = $(this),
					key = $b.attr("data-key");
				btnPressed = key;
				var cnt = ++pressCount;
				setTimeout(function() {
					if (pressCount == cnt && btnPressed == key) {
						editButton($b);
					}
					btnPressed = null;
				}, 750);
			}).bind(evtEnd, function() {
				var $b = $(this),
					orgColor = $b.attr("data-color"),
					curColor = $b.css("background-color");
				if (btnPressed == $b.attr("data-key")) {
					if (orgColor == curColor) {
						$b.css("background-color", "#ccc");
					} else {
						$b.css("background-color", orgColor);
					}
				}
				btnPressed = null;
			})
			$("#button-color").change(function() {
				var value = $(this).val();
				$(this).css("background-color", value);
			})
			$("#button-save").click(updateButton);
		}
		function editButton($b) {
			$editingButton = $b;
			$("#button-caption").val($b.find("div").text());
			$("#button-color").val(rgb2hex($b.attr("data-color"))).change();
			$("#button-dialog").modal();
		}
		function updateButton() {
console.log("test1: " + $editingButton);
			if ($editingButton) {
				$editingButton.find("div").text($("#button-caption").val());
				$editingButton.css("background-color", $("#button-color").val());
				$editingButton.attr("data-color", $editingButton.css("background-color"));
			}
			$("#button-dialog").modal("hide");
		}
		$("#pattern").patternInput({
			"onFinish" : function(value) {
				value = value.join("")
				alert("OK: " + value);
			}
		});
		var ctrl = new Control($("#ctrl")),
			btnPressed = null,
			pressCount = 0,
			$editingButton = null;
		init();
	}
})
