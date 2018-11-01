"use strict";

angular.module('demoAppModule').controller('CreateProductModalCtrl', function($http, $uibModalInstance, $uibModal, apiBaseURL, peers) {
    const createProductModal = this;

    createProductModal.peers = peers;
    createProductModal.form = {};
    createProductModal.formError = false;

    /** Validate and create an Product. */
    createProductModal.create = () => {
        if (invalidFormInput()) {
            createProductModal.formError = true;
        } else {
            createProductModal.formError = false;

            const buysell = createProductModal.form.buysell;
            const counterparty = createProductModal.form.counterparty;
            const productName = createProductModal.form.productName;
            const currency = createProductModal.form.currency;
            const quantity = createProductModal.form.quantity;
            const amount = quantity*10;
            $uibModalInstance.close();

            // We define the Product creation endpoint.
            const issueProductEndpoint =
                apiBaseURL +
                `issue-product-buy-sell?buysell=${buysell}&counterparty=${counterparty}&productName=${productName}&currency=${currency}&quantity=${quantity}&amount=${amount}`;

            // We hit the endpoint to create the Product and handle success/failure responses.
            $http.get(issueProductEndpoint).then(
                (result) => createProductModal.displayMessage(result),
                (result) => createProductModal.displayMessage(result)
            );
        }
    };

    /** Displays the success/failure response from attempting to create an Product. */
    createProductModal.displayMessage = (message) => {
        const createProductMsgModal = $uibModal.open({
            templateUrl: 'createProductMsgModal.html',
            controller: 'createProductMsgModalCtrl',
            controllerAs: 'createProductMsgModal',
            resolve: {
                message: () => message
            }
        });

        // No behaviour on close / dismiss.
        createProductMsgModal.result.then(() => {}, () => {});
    };

    /** Closes the Product creation modal. */
    createProductModal.cancel = () => $uibModalInstance.dismiss();

    // Validates the Product.
    function invalidFormInput() {
        return isNaN(createProductModal.form.quantity) || (createProductModal.form.counterparty === undefined);
    }
});

// Controller for the success/fail modal.
angular.module('demoAppModule').controller('createProductMsgModalCtrl', function($uibModalInstance, message) {
    const createProductMsgModal = this;
    createProductMsgModal.message = message.data;
});