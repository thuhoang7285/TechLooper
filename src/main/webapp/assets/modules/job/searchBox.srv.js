angular.module("Jobs").factory("searchBoxService", function ($location, jsonValue) {
  var scope;
  var abc;

  return {
    refresh : function() {
      selectBox.refresh();
    },

    initializeIntelligent: function ($scope) {
      scope = $scope;
      var hWin = $(window).height();
      var keyWords = '';
      openSearchForm($(window).height());
      $(window).resize(function () {
        openSearchForm($(window).height());
      });

      function openSearchForm(h) {
        $('.search-block').animate({
          'min-height': h,
          bottom: 0
        }, {
          duration: '10000',
          easing: 'easeOutQuad'
        });
        $('body').css("background-color", "#fff");
      }

      $('.btn-close').click(function () {
        $('.search-block').animate({
          'height': 0,
          'bottom': '50%'
        }, {
          duration: '10000',
          easing: 'easeOutQuad'
        });
        $('body').css("background-color", "#2e272a");
      });

      // load data for auto dropdown list and show technical skill
      var dataSkill = jsonValue.technicalSkill,
        options = '';
      $.each(dataSkill, function (index, skill) {
        options = options + '<option value="' + index + '" data-left="<img src=images/' + skill.logo + '>">' + skill.name + '</option>';
      });
      $('.termsList').append(options);

      var select = $('.termsList');
      selectBox = select.selectator({
        showAllOptionsOnFocus: true,
        $selectorsList: $('.technical-Skill-List ul')
      });
      $('.search-form').click(function () {
        if (!$('.selectator_chosen_items').is(':empty') || $('input.selectator_input').val() != '') {
          keyWords = "";
          getKeyWords();
          if (keyWords != '') {
            $location.path("/jobs/search/" + keyWords);
            scope.$apply();
          }
        }
      });

      function getKeyWords() {
        var $this = $('.selectator_chosen_items').find('.selectator_chosen_item_title');
        $this.each(function () {
          keyWords = keyWords + ' ' + $(this).text();
        });
        var val = $('input.selectator_input').val();
        if(val != ''){
          keyWords = keyWords + ' ' + $('input.selectator_input').val();
        }
        keyWords = keyWords.substring(1);
        return keyWords;
      }
    }
  }
});
