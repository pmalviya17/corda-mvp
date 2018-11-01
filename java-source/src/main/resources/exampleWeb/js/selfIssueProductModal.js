"use strict";

// Similar to the IOU creation modal - see createIOUModal.js for comments.
angular.module('demoAppModule').controller('SelfIssueProductModalCtrl', function($http, $uibModalInstance, $uibModal, apiBaseURL) {
    const selfIssueProductModal = this;

    selfIssueProductModal.form = {};
    selfIssueProductModal.formError = false;

    selfIssueProductModal.issue = () => {
        if (invalidFormInput()) {
            selfIssueProductModal.formError = true;
        } else {
            selfIssueProductModal.formError = false;

            const quantity = selfIssueProductModal.form.quantity;
            const productName = selfIssueProductModal.form.productName;

            $uibModalInstance.close();

            const selfIssueProductEndpoint =
                apiBaseURL +
                `self-issue-product?quantity=${quantity}&productName=${productName}`;

            $http.get(selfIssueProductEndpoint).then(
                (result) => {console.log(result.toString()); selfIssueProductModal.displayMessage(result); },
                (result) => {console.log(result.toString()); selfIssueProductModal.displayMessage(result); }
            );
        }
    };

    selfIssueProductModal.displayMessage = (message) => {
        const selfIssueProductMsgModal = $uibModal.open({
            templateUrl: 'selfIssueProductMsgModal.html',
            controller: 'selfIssueProductMsgModalCtrl',
            controllerAs: 'selfIssueProductMsgModal',
            resolve: {
                message: () => message
            }
        });

        selfIssueProductMsgModal.result.then(() => {}, () => {});
    };

    selfIssueProductModal.cancel = () => $uibModalInstance.dismiss();

    function invalidFormInput() {
        return isNaN(selfIssueProductModal.form.quantity) || (selfIssueProductModal.form.productName.length != 3);
    }
});

angular.module('demoAppModule').controller('selfIssueProductMsgModalCtrl', function($uibModalInstance, message) {
    const selfIssueProductMsgModal = this;
    selfIssueProductMsgModal.message = message.data;
});