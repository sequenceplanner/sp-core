// 1.3.3 docs: https://github.com/angular-gantt/angular-gantt/blob/1.3.x/docs/index.html
function SPGantt(element, options) {

  'use strict';

  var facadedObject = {};

  var app = angular.module('ganttApp', ['gantt', 'gantt.tooltips', 'gantt.table']);

  function ganttCtrl($scope) {
      facadedObject.setData = function(rows) {
        $scope.data = rows;
        $scope.$apply();
      };
      facadedObject.addSomeRow = function() {
        $scope.addSomeRow();
        $scope.$apply();
      };
      facadedObject.addRow = function(row) {
        $scope.data.push(row);
        $scope.$apply();
      };

      $scope.headers = options.headers;

      $scope.headersFormats = {
        hour: 'H:mm',
        minute: 'H:mm',
        second: 'ss'
      };
      /*
       * TODO this would make time-label appear every 20 minutes
       * (as appropriate in erica I think), maybe not available unless
       * we get the latest version of angular-gantt
      var twentyMinutes = moment.duration({'minutes': 20});
      $scope.headersScales = {
        minute: twentyMinutes
      };
      */

      /*
      var ctrl = this;
      var testPrint = function() {
        console.log("this is a way to test added functions");
      };
      ctrl.testPrint = testPrint;
      facadedObject.testPrint = testPrint;
      */

      // without this, we get an error if react calls scroll before $scope.on.ready has fired
      facadedObject.scroll = function(dx) { };
      var onUserScrollCB = function() { };
      facadedObject.onUserScroll = function(callback) {
        onUserScrollCB = callback;
      };

      $scope.registerApi = function(api) {
        api.core.on.ready($scope, function() {

          facadedObject.scroll = function(dx) {
            if(dx >= 0) {
              api.scroll.right(dx);
            } else {
              api.scroll.left(-dx);
            }
          };

          api.scroll.on.scroll($scope, function(left, date, direction) {
            onUserScrollCB();
          });

        });
      };

      $scope.viewScale = options.viewScale;
  }

  app.component("ganttComponent", {
    template: `
          <!-- <button ng-click="$ctrl.testPrint()">call testPrint</button> -->
          <div gantt data="data" api="registerApi" headers="headers" headers-formats="headersFormats" headers-scales="headersScales" view-scale="viewScale" column-width="50">
            <!-- TODO need to fix some dependency stuff if we want this
            <gantt-tree></gantt-tree>
            -->
            <gantt-tooltips date-format="'H:mm:ss'" delay="100"></gantt-tooltips>
            <gantt-table headers="{'model.name': ''}"></gantt-table>
          </div>
      `
      ,
    controller: ganttCtrl
  });

  angular.bootstrap(element, ['ganttApp']);

  return facadedObject;
}
