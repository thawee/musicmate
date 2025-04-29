new Vue({
    el: '#app',
    data: {
        musicList: [],
        selectedMusic: null,
        editedMusic: {},
        editMode: false,
        searchQuery: '',
        searchType: 'all',
        targetDirectory: '',
        toast: {
            visible: false,
            message: '',
            timeout: null
        }
    },
    created() {
        this.fetchMusicList();
    },
    methods: {
        fetchMusicList() {
            axios.get('/api/music')
                .then(response => {
                    this.musicList = response.data;
                })
                .catch(error => {
                    //console.error('Error fetching music list:', error.response ? error.response.status : 'No response');
                    console.error('Error details:', error.message);
                    this.showToast('Failed to load music library: ' + error.message);
                });
        },

        searchMusic() {
            const query = this.searchQuery.trim();
            if (!query) {
                this.fetchMusicList();
                return;
            }

            axios.get(`/api/music?q=${encodeURIComponent(query)}&type=${this.searchType}`)
                .then(response => {
                    this.musicList = response.data;
                    if (this.musicList.length === 0) {
                        this.showToast('No music found matching your search');
                    }
                })
                .catch(error => {
                    console.error('Error searching music:', error);
                    this.showToast('Search failed');
                });
        },

        selectMusic(music) {
            axios.get(`/api/music/${music.id}`)
                .then(response => {
                    this.selectedMusic = response.data;
                    // Create a copy for editing
                    this.editedMusic = { ...this.selectedMusic };
                    this.editMode = true;
                })
                .catch(error => {
                    console.error('Error fetching music details:', error);
                    this.showToast('Failed to load music details');
                });
        },

        cancelEdit() {
            this.editMode = false;
            this.selectedMusic = null;
            this.editedMusic = {};
            this.targetDirectory = '';
        },

        saveChanges() {
            // Prepare the data to send
            const updatedData = {
                title: this.editedMusic.title,
                artist: this.editedMusic.artist,
                album: this.editedMusic.album,
                genre: this.editedMusic.genre,
                grouping: this.editedMusic.grouping
            };

            axios.put(`/api/music/${this.selectedMusic.id}`, updatedData)
                .then(response => {
                    this.showToast('Tags updated successfully');

                    // Update the selected music with the edited values
                    this.selectedMusic = { ...this.selectedMusic, ...this.editedMusic };

                    // Update the music in the list
                    const index = this.musicList.findIndex(m => m.id === this.selectedMusic.id);
                    if (index !== -1) {
                        this.musicList[index] = { ...this.musicList[index], ...this.editedMusic };
                    }
                })
                .catch(error => {
                    console.error('Error updating music tags:', error);
                    this.showToast('Failed to update tags');
                });
        },

        moveFile() {
            if (!this.targetDirectory) {
                this.showToast('Please select a target directory');
                return;
            }

            // Create a request to move the file
            const moveData = {
                targetDirectory: this.targetDirectory
            };

            axios.post(`/api/music/${this.selectedMusic.id}/move`, moveData)
                .then(response => {
                    this.showToast('File moved successfully');

                    // Update the path in the UI
                    this.editedMusic.path = response.data.newPath;
                    this.selectedMusic.path = response.data.newPath;

                    // Update the music in the list
                    const index = this.musicList.findIndex(m => m.id === this.selectedMusic.id);
                    if (index !== -1) {
                        this.musicList[index].path = response.data.newPath;
                    }

                    // Reset the target directory
                    this.targetDirectory = '';
                })
                .catch(error => {
                    console.error('Error moving file:', error);
                    this.showToast('Failed to move file');
                });
        },

        showToast(message) {
            // Clear any existing timeout
            if (this.toast.timeout) {
                clearTimeout(this.toast.timeout);
            }

            // Show the toast
            this.toast.message = message;
            this.toast.visible = true;

            // Hide the toast after 3 seconds
            this.toast.timeout = setTimeout(() => {
                this.toast.visible = false;
            }, 3000);
        }
    }
});
