import React, {useState, useEffect} from 'react';
import {
    Button, Dialog, DialogContent, DialogTitle,
    DialogActions, TextField, Typography
} from '@material-ui/core';

const WorkspaceDeletion = ({open, workspaceId, onClose, onConfirm}) => {
    const [enteredWSId, setEnteredWSId] = useState('');

    // reset user input
    useEffect(() => {
        setEnteredWSId('');
    }, [open, workspaceId]);

    const enteredWSIdMatch = enteredWSId === workspaceId;

    return (
        <Dialog
            open={open}
            onClose={onClose}
        >
            <DialogTitle>Delete Workspace</DialogTitle>
            <DialogContent>
                <Typography
                    variant="inherit"
                >
                    This is a permanent action and cannot be undone.
                    It will remove the given workspace including all of its files, metadata and users.
                </Typography>
                <TextField
                    label="Please type in the workspace id"
                    value={enteredWSId}
                    onChange={(e) => setEnteredWSId(e.target.value)}
                    margin="normal"
                    fullWidth
                />
            </DialogContent>
            <DialogActions>
                <Button
                    onClick={onClose}
                    color="secondary"
                >
                    Cancel
                </Button>
                <Button
                    onClick={onConfirm}
                    disabled={!enteredWSIdMatch}
                >
                    <Typography
                        variant="inherit"
                        color={enteredWSIdMatch ? 'error' : 'textSecondary'}
                    >
                        Delete workspace forever
                    </Typography>
                </Button>
            </DialogActions>
        </Dialog>
    );
};

export default WorkspaceDeletion;
