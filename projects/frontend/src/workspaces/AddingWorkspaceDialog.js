import Dialog from "@material-ui/core/Dialog";
import DialogTitle from "@material-ui/core/DialogTitle";
import React from "react";
import Typography from "@material-ui/core/Typography";
import DialogActions from "@material-ui/core/DialogActions";
import Button from "@material-ui/core/Button";

export default ({onClose}) => (
    <Dialog
        open
        onClose={onClose}
        aria-labelledby="form-dialog-title"
        fullWidth
        maxWidth="md"
    >
        <DialogTitle disableTypography id="form-dialog-title">
            <Typography variant="h5">Adding a new Workspace</Typography>
            <Typography variant="subtitle1">Your new workspace is being created. That might take some time.</Typography>
        </DialogTitle>
        <DialogActions>
            <Button
                onClick={onClose}
                color="primary"
                variant="contained"
            >
                Ok
            </Button>
        </DialogActions>
    </Dialog>);